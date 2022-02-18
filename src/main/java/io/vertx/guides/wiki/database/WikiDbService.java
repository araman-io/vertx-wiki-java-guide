package io.vertx.guides.wiki.database;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.serviceproxy.ServiceProxyBuilder;

@ProxyGen
public interface WikiDbService {

  public Future<JsonObject> initDatabase();

  public Future<JsonObject> fetchAllPages();

  public Future<JsonObject> renderPage(JsonObject message);

  public Future<JsonObject> deletePage(JsonObject message);

  public Future<JsonObject> upsertPage(JsonObject message);


  static WikiDbService create(JDBCPool pool) {
    return new WikiDbServiceImpl(pool);
  }

  static WikiDbService createProxy(Vertx vertx, String address) {
    return new ServiceProxyBuilder(vertx).setAddress(address).build(WikiDbService.class);
  }
}
