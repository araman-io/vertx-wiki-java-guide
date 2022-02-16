package io.vertx.guides.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.Date;

public class MainVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);
  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";
  private JDBCPool pool;
  private FreeMarkerTemplateEngine templateEngine;
  private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n" + "\n" + "Feel-free to write in Markdown!\n";

  @Override
  public void start(Promise<Void> startPromise) {
    prepareDatabase().compose(result -> startHttpServer()).onSuccess(result -> {
      LOGGER.info("db and httpserver started");
      startPromise.complete();
    }).onFailure(error -> {
      LOGGER.error("something went wrong {}", error);
      startPromise.fail(error);
    });
  }

  private Future<HttpServer> startHttpServer() {
    HttpServer httpServer = vertx.createHttpServer();

    templateEngine = FreeMarkerTemplateEngine.create(vertx);

    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post().handler(BodyHandler.create());  // <4>
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);

    return httpServer.requestHandler(router).listen(8080).onSuccess(result -> {
      LOGGER.info("HTTP server running on port 8080");
    }).onFailure(error -> {
      LOGGER.error("something went wrong {}", error);
    });
  }

  private void pageDeletionHandler(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");
    LOGGER.info("going to delete page " + id);
    this.pool.preparedQuery(SQL_DELETE_PAGE)
      .execute(Tuple.of(id))
      .onSuccess(rows -> {
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
    String id = routingContext.request().getParam("id");
    String title = routingContext.request().getParam("title");
    boolean newPage = "yes".equals(routingContext.request().getParam("newPage"));
    String markdown = routingContext.request().getParam("markdown");
    Future<RowSet<Row>> preparedQuery;
    if (newPage) {
      preparedQuery = this.pool.preparedQuery(SQL_CREATE_PAGE)
        .execute(Tuple.of(title, markdown));
    } else {
      preparedQuery = this.pool.preparedQuery(SQL_SAVE_PAGE)
        .execute(Tuple.of(markdown, id));
    }

    preparedQuery
      .onSuccess(rows -> {
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
    this.pool.preparedQuery(SQL_GET_PAGE).execute(Tuple.of(pageName))
      .compose(rows -> {
        JsonObject templateData = new JsonObject();
        RowIterator<Row> iterator = rows.iterator();
        if (iterator.hasNext()) {
          Row row = iterator.next();
          templateData.put("id", row.getInteger(0));
          templateData.put("rawContent", row.getString("CONTENT"));
          templateData.put("newPage", "no");
        } else {
          templateData.put("id", -1);
          templateData.put("rawContent", EMPTY_PAGE_MARKDOWN);
          templateData.put("newPage", "yes");
        }
        templateData.put("title", pageName);
        templateData.put("timestamp", new Date().toString());
        templateData.put("content", Processor.process(templateData.getString("rawContent")));
        return Future.succeededFuture(templateData);
      })
      .compose(templateData -> {
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

    this.pool.query(SQL_ALL_PAGES).execute().compose(rows -> {
        JsonObject templateData = new JsonObject().put("title", "Home of our Wiki!!!");
        JsonArray pages = new JsonArray();
        for (Row row : rows)
          pages.add(row.getString("NAME"));
        templateData.put("pages", pages);
        return Future.succeededFuture(templateData);
      })
      .compose(templateData -> {
        return templateEngine.render(templateData, "templates/index.ftl");
      })
      .onSuccess(data -> {
        routingContext.response().putHeader("Content-Type", "text/html");
        routingContext.response().end(data.toString());
      })
      .onFailure(error -> {
        routingContext.fail(500, error);
      });
  }

  private Future<RowSet<Row>> prepareDatabase() {
    this.pool = JDBCPool.pool(vertx, new JsonObject()
      .put("url", "jdbc:hsqldb:file:db/wiki")
      .put("driver_class", "org.hsqldb.jdbcDriver").put("max_pool_size", 30));

    return pool.query(SQL_CREATE_PAGES_TABLE).execute()
      .onSuccess(result -> {
        LOGGER.info("database has been initialized");
      })
      .onFailure(error -> {
        LOGGER.info("something went wrong with the db step  {}", error);
      });
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(MainVerticle.class.getName());
  }


}
