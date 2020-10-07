package MyApp;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class MyServer {

  public static void main(final String[] args) {
    final MyServer myServer = new MyServer();
  }

  private final Vertx vertx;
  private final boolean debug = true;

  MyServer() {

    final VertxOptions options = new VertxOptions();
    if(debug) {
      options.setBlockedThreadCheckInterval(Long.MAX_VALUE >> 2);
    }

    vertx = Vertx.vertx(options);
    vertx.deployVerticle(new HttpServerVerticle());
//    vertx.deployVerticle(new CouchbaseVerticle());

  }

}