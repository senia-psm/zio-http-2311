package info.senia

import zio.*
import zio.http.*
import zio.http.codec.QueryCodec
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.{Endpoint, EndpointExecutor, EndpointLocator, EndpointMiddleware}

object App extends ZIOAppDefault {

  val a: QueryCodec[String] = query("a").transformOrFailLeft[String](s => Left("error"), x => x)

  val test: Endpoint[Option[String], Nothing, String, EndpointMiddleware.None] =
    Endpoint
      .get("test")
      .query(a.optional)
      .out[String]


  def app: App[Any] = {

    val testImpl = test.implement{a =>
      ZIO.succeed(s"a: $a")
    }

    testImpl.toApp
  }

  val server = Server.serve(app @@ RequestHandlerMiddlewares.requestLogging()).provide(Server.configured()).forkDaemon

  val client = {
    for {
      _ <- ZIO.sleep(5.seconds)
      client <- ZIO.service[Client]
      url <- ZIO.fromEither(URL.decode("http://localhost:8080/"))
      executor = EndpointExecutor(client, EndpointLocator.fromURL(url), ZIO.unit)
      _ <- executor(test(Some("aaa"))).debug("Response")
    } yield ()

  }

  def run: Task[Unit] =
    (server <&> client.provide(Client.default)).unit
}
