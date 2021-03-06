package com.thenewmotion.ocpi
package tokens

import java.time.ZonedDateTime
import _root_.akka.http.scaladsl.HttpExt
import _root_.akka.http.scaladsl.model.Uri
import _root_.akka.stream.Materializer
import cats.effect.{Async, ContextShift}
import com.thenewmotion.ocpi.common.{ErrRespUnMar, OcpiClient, PagedRespUnMar}
import com.thenewmotion.ocpi.msgs.Ownership.Ours
import com.thenewmotion.ocpi.msgs.v2_1.Tokens.Token
import com.thenewmotion.ocpi.msgs.{AuthToken, ErrorResp}
import scala.concurrent.ExecutionContext

class TokensClient[F[_]: Async](
  implicit http: HttpExt,
  errorU: ErrRespUnMar,
  sucU: PagedRespUnMar[Token]
) extends OcpiClient[F] {

  def getTokens(
    uri: Uri,
    auth: AuthToken[Ours],
    dateFrom: Option[ZonedDateTime] = None,
    dateTo: Option[ZonedDateTime] = None,
    pageLimit: Int = OcpiClient.DefaultPageLimit
  )(
    implicit ec: ExecutionContext,
    cs: ContextShift[F],
    mat: Materializer
  ): F[Either[ErrorResp, Iterable[Token]]] =
    traversePaginatedResource[Token](uri, auth, dateFrom, dateTo, pageLimit)
}
