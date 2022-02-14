package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class MainVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);
  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";
  private JDBCPool pool;

  @Override
  public void start(Promise<Void> startPromise) {
    prepareDatabase()
      .compose(result -> startHttpServer())
      .onSuccess(result -> startPromise.complete())
      .onFailure(error -> {
        LOGGER.error("something went wrong", error);
      });
  }

  private Future<HttpServer> startHttpServer() {
    HttpServer httpServer = vertx.createHttpServer();
    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);
    return httpServer.requestHandler(router).listen(8080);
  }

  private void pageDeletionHandler(RoutingContext routingContext) {

  }

  private void pageCreateHandler(RoutingContext routingContext) {

  }

  private void pageUpdateHandler(RoutingContext routingContext) {

  }

  private void pageRenderingHandler(RoutingContext routingContext) {

  }

  private void indexHandler(RoutingContext routingContext) {
    context.put("title", "Wiki home");
  }

  private Future<RowSet<Row>> prepareDatabase() {
    this.pool = JDBCPool.pool(vertx, new JsonObject()
      .put("url", "jdbc:hsqldb:file:db/wiki")
      .put("driver_class", "org.hsqldb.jdbcDriver")
      .put("max_pool_size", 30));

    return pool.query(SQL_CREATE_PAGES_TABLE)
      .execute();
  }


}
