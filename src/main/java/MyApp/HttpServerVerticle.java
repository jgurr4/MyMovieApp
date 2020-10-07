package MyApp;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

//import io.vertx.reactivex.ext.web.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.*;
import io.reactivex.annotations.Nullable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.*;
import io.vertx.reactivex.core.http.*;
import io.vertx.reactivex.ext.web.*;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import io.vertx.reactivex.ext.web.sstore.SessionStore;
import io.vertx.reactivex.core.eventbus.EventBus;

public class HttpServerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

  @Override
  public Completable rxStart() {
    HttpServer server = vertx.createHttpServer();

    Router router = Router.router(vertx);
    SessionStore store = LocalSessionStore.create(vertx);
    SessionHandler mySesh = SessionHandler.create(store);
    mySesh.setSessionTimeout(86400000); //24 hours in milliseconds.
    mySesh.setSessionCookieName("cookieName");
    mySesh.setSessionCookiePath("cookiePath");   //TODO: Ask if my step by step guide is right about what the purpose of this is.

    router.route().handler(BodyHandler.create());
    router.route().handler(mySesh);   //FIXME: Ask if this is right place, because it only happens for post requests with html login forms.
    router.get("/static/*").handler(this::staticHandler);
    router.post("/bus/*").handler(this::busHandler);
    final int port = 8080;
    final Single<HttpServer> rxListen = server
        .requestHandler(router)
        .rxListen(port)
        .doOnSuccess(e -> {
          LOGGER.info("HTTP server running on port " + port);
        })
        .doOnError(e -> {
          LOGGER.error("Could not start a HTTP server", e.getMessage());
        });

    return rxListen.ignoreElement();
  }


  private void staticHandler(RoutingContext context) {
    //TODO: Add HTML and CSS files to project in static folder.

    final HttpServerResponse response = context.response();
    final HttpServerRequest request = context.request();
    @Nullable
    String path = request.path();
    try {
      LOGGER.debug("GET " + path);
      path = path.substring(1);
      final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
      if (stream != null) {
        final String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
            .collect(Collectors.joining("\n"));
        if (path.endsWith(".html")) {
          response.putHeader("Content-Type", "text/html");
        } else if (path.endsWith(".css")) {
          response.putHeader("Content-Type", "text/css");
        } else {
          response.end("<html><body>Error filetype unknown: " + path + "</body></html>");
        }
        response.setStatusCode(200);
        response.end(text);
      } else {
        LOGGER.warn("Resource not found: " + path);
        response.setStatusCode(404);
        response.end();
      }
    } catch (Exception e) {
      LOGGER.error("Problem fetching static file: " + path, e);
      response.setStatusCode(502);
      response.end();
    }
  }

  private void busHandler(RoutingContext context) {
    //TODO: For Couchbase Verticle to work, make sure to add a bucket in database beforehand that works with cookies and username/password/email etc...

    Session session = context.session();
    session.get(SessionKey.username.name());  //If username is null force user to login on login page.
    session.put(SessionKey.username.name(), "username");
    //These will be variables. the values will come from database.
    //The key will never change it will just be user.

    //TODO: Check if this Cookie code is necessary or not. If not delete it.
//    Cookie userCookie = context.getCookie("myCookie");
//    String cookieValue = userCookie.getValue();
//    context.addCookie(Cookie.cookie("name", "value"));


    final EventBus eb = vertx.eventBus();

    final HttpServerRequest request = context.request();
    final MultiMap params = request.params();

    JsonObject object = new JsonObject();
    for (Map.Entry<String, String> entry : params.entries()) {
      object.put(entry.getKey(), entry.getValue());
    }

    final String absoluteURI = request.absoluteURI();
    LOGGER.debug("absoluteURI=" + absoluteURI);
    final String busAddress = absoluteURI.replaceAll("^.*/bus/", "");
    LOGGER.debug("busAddress=" + busAddress);
    eb.rxRequest(busAddress, object.encode())                   //sends the json object with request params to UserVerticle to whichever consumer specified by busAddress.
        .doOnSuccess(e -> {
          LOGGER.debug("HttpServer Verticle Received username: " + e.body());
        })
        .doOnError(e -> {
          LOGGER.debug("Error with busAddress " + busAddress + " " + e.getMessage());
          //put some method to notify browser that the login was unsuccessful, and try again.
          eb.send("loginForm", "That username or password is invalid.");
        });
  }
}



