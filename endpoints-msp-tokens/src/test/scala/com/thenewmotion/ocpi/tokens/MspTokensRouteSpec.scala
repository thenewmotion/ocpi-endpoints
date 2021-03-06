package com.thenewmotion.ocpi
package tokens

import java.time.ZonedDateTime
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Link, RawHeader}
import akka.http.scaladsl.testkit.Specs2RouteTest
import cats.effect.IO
import cats.syntax.either._
import com.thenewmotion.ocpi.common._
import com.thenewmotion.ocpi.msgs.OcpiStatusCode._
import com.thenewmotion.ocpi.msgs._
import com.thenewmotion.ocpi.msgs.sprayjson.v2_1.protocol._
import com.thenewmotion.ocpi.msgs.v2_1.Locations.{ConnectorId, EvseUid, LocationId}
import com.thenewmotion.ocpi.msgs.v2_1.Tokens._
import com.thenewmotion.ocpi.tokens.AuthorizeError._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class MspTokensRouteSpec extends Specification with Specs2RouteTest with Mockito {

  "MspTokensRoute" should {
    "return a paged set of Tokens" in new TestScope {
      service.tokens(apiUser, Pager(0, 1000), None, None) returns IO.pure(PaginatedResult(List(token), 1))
      Get() ~> route(apiUser) ~> check {
        header[Link] must beNone
        headers.find(_.name == "X-Limit") mustEqual Some(RawHeader("X-Limit", "1000"))
        headers.find(_.name == "X-Total-Count") mustEqual Some(RawHeader("X-Total-Count", "1"))
        there was one(service).tokens(apiUser, Pager(0, 1000), None, None)
        val res = entityAs[SuccessResp[List[Token]]]
        res.data mustEqual List(token)
      }
    }

    "authorize without location references" in new TestScope {
      service.authorize(apiUser, TokenUid("23455655A"), None) returns IO.pure(AuthorizationInfo(Allowed.Allowed).asRight)

      Post("/23455655A/authorize") ~> route(apiUser) ~> check {
        there was one(service).authorize(apiUser, TokenUid("23455655A"), None)
        val res = entityAs[SuccessResp[AuthorizationInfo]]
        res.data.allowed mustEqual Allowed.Allowed
      }
    }

    "authorize with location references" in new TestScope {

      val lr = LocationReferences(LocationId("1234"), List(EvseUid("1234")), List(ConnectorId("1234"), ConnectorId("5678")))

      service.authorize(apiUser, TokenUid("23455655A"), Some(lr)) returns IO.pure(AuthorizationInfo(Allowed.Allowed).asRight)

      Post("/23455655A/authorize", lr) ~> route(apiUser) ~> check {
        there was one(service).authorize(apiUser, TokenUid("23455655A"), Some(lr))
        val res = entityAs[SuccessResp[AuthorizationInfo]]
        res.data.allowed mustEqual Allowed.Allowed
      }
    }

    "handle MustProvideLocationReferences failure" in new TestScope {
      service.authorize(apiUser, TokenUid("23455655A"), None) returns IO.pure(MustProvideLocationReferences.asLeft)

      Post("/23455655A/authorize") ~> route(apiUser) ~> check {
        there was one(service).authorize(apiUser, TokenUid("23455655A"), None)
        val res = entityAs[ErrorResp]
        res.statusCode mustEqual NotEnoughInformation
        status mustEqual StatusCodes.OK
      }
    }

    "handle NotFound failure" in new TestScope {
      service.authorize(apiUser, TokenUid("23455655A"), None) returns IO.pure(TokenNotFound.asLeft)

      Post("/23455655A/authorize") ~> route(apiUser) ~> check {
        there was one(service).authorize(apiUser, TokenUid("23455655A"), None)
        status mustEqual StatusCodes.NotFound
      }
    }
  }

  trait TestScope extends Scope with OcpiDirectives with SprayJsonSupport {
    val apiUser = GlobalPartyId("NL", "TNM")

    val token = Token(
      uid = TokenUid("23455655A"),
      `type` = TokenType.Rfid,
      authId = AuthId("NL-TNM-000660755-V"),
      visualNumber = Some("NL-TNM-066075-5"),
      issuer = "TheNewMotion",
      valid = true,
      whitelist = WhitelistType.Allowed,
      lastUpdated = ZonedDateTime.parse("2017-01-24T10:00:00.000Z")
    )

    import HktMarshallableFromECInstances._

    val service = mock[MspTokensService[IO]]
    val route = MspTokensRoute(service)
  }
}
