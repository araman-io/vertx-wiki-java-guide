package io.vertx.guides.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import io.vertx.guides.wiki.database.WikiDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Integer.valueOf;

public class HttpVerticle extends AbstractVerticle {

  private WikiDbService proxy;
  private FreeMarkerTemplateEngine templateEngine;
  private Logger LOGGER = LoggerFactory.getLogger(HttpVerticle.class);
  private WebClient webClient;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    HttpServer httpServer = vertx.createHttpServer();
    this.proxy = WikiDbService.createProxy(vertx, "wikidb.queue");
    this.webClient = WebClient.create(vertx, new WebClientOptions()
      .setSsl(true)
      .setUserAgent("vert-x3"));

    templateEngine = FreeMarkerTemplateEngine.create(vertx);

    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);
    router.get("/backup").handler(this::backupHandler);

    httpServer.requestHandler(router)
      .listen(8080)
      .onSuccess(result -> {
        LOGGER.info("HTTP server running on port 8080");
        startPromise.complete();
      }).onFailure(error -> {
        LOGGER.error("something went wrong {}", error);
        startPromise.fail(error);
      });
  }

  private void pageDeletionHandler(RoutingContext routingContext) {
    Integer id = valueOf(routingContext.request().getParam("id"));
    JsonObject requestPayload = new JsonObject().put("id", id);
    LOGGER.info("going to delete page " + id);

    this.proxy.deletePage(requestPayload)
      .onSuccess(message -> {
        routingContext.redirect("/");
      })
      .onFailure(error -> {
        routingContext.fail(error);
      });
  }

  private void pageCreateHandler(RoutingContext routingContext) {
    String pageName = routingContext.request().getParam("pageName");
    String location;
    if (pageName == null || pageName.isEmpty()) {
      location = "/";
    } else {
      location = "/wiki/" + pageName;
    }
    LOGGER.info("retrieved name " + pageName + " and location " + location);
    routingContext.response().setStatusCode(303).putHeader("Location", location).end();
  }

  private void pageUpdateHandler(RoutingContext routingContext) {
    Integer id = valueOf(routingContext.request().getParam("id"));
    String title = routingContext.request().getParam("title");
    boolean newPage = "yes".equals(routingContext.request().getParam("newPage"));
    String markdown = routingContext.request().getParam("markdown");

    JsonObject requestPayload = new JsonObject()
      .put("id", id)
      .put("title", title)
      .put("newPage", newPage)
      .put("markDown", markdown);

    this.proxy.upsertPage(requestPayload)
      .onSuccess(message -> {
        routingContext.response().setStatusCode(303);
        routingContext.response().putHeader("Location", "/wiki/" + title);
        routingContext.response().end();
      })
      .onFailure(error -> {
        routingContext.fail(error);
      });

  }

  private void pageRenderingHandler(RoutingContext routingContext) {
    String pageName = routingContext.pathParam("page");
    JsonObject requestPayload = new JsonObject().put("page", pageName);
    this.proxy.renderPage(requestPayload).compose(response -> {
        JsonObject templateData = response.copy();
        templateData.put("content", Processor.process(templateData.getString("rawContent")));
        return templateEngine.render(templateData, "templates/page.ftl");
      })
      .onSuccess(data -> {
        routingContext.response().putHeader("Content-Type", "text/html");
        routingContext.response().end(data.toString());
      })
      .onFailure(error -> {
        routingContext.fail(500, error);
      });
  }

  private void indexHandler(RoutingContext routingContext) {
    this.proxy
      .fetchAllPages()
      .compose(message -> {
        routingContext.put("title", "Home of our Wiki!!!");
        routingContext.put("pages", message.getJsonArray("pages").getList());
        return templateEngine.render(routingContext.data(), "templates/index.ftl");
      })
      .onSuccess(data -> {
        routingContext.response().putHeader("Content-Type", "text/html");
        routingContext.response().end(data.toString());
      })
      .onFailure(error -> {
        routingContext.fail(500, error);
      });
  }

  private void backupHandler(RoutingContext routingContext) {
    this.proxy.getAllPageData()
      .compose(pageData -> {
        JsonObject pages = pageData.copy()
          .put("language", "plaintext")
          .put("title", "vertx-wiki-backup-ar")
          .put("public", true);

        return webClient.post(443, "glot.io", "/api/snippets") // <3>
          .putHeader("Content-Type", "application/json")
          .sendJsonObject(pages);
      })
      .onSuccess(response -> {
        if (response.statusCode() == 200) {
          String url = "https://glot.io/snippets/" + response.bodyAsJsonObject().getString("id");
          LOGGER.info("backup url is {}", url);
          routingContext.put("backup_gist_url", url);  // <6>
          indexHandler(routingContext);
        } else {
          StringBuilder message = new StringBuilder().append("Could not backup the wiki: ").append(response.statusMessage());
          JsonObject body = response.body().toJsonObject();
          if (body != null) {
            message.append(System.getProperty("line.separator")).append(body.encodePrettily());
          }
          LOGGER.error(message.toString());
          routingContext.fail(502);
        }
      })
      .onFailure(error -> {
        routingContext.fail(500, error);
      });
  }

}
