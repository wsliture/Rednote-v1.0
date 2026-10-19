package com.shengid.rednote;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "logging.file.name=target/test-prod.log")
@ActiveProfiles({"prod", "test"})
class ProductionCacheTests {
  @LocalServerPort int port;

  @Test
  void productionAssetsRevalidateWhileHtmlAndErrorsStayUncacheable() throws Exception {
    var client = HttpClient.newHttpClient();
    for (String path : new String[] {"/css/integration.css", "/js/app.js", "/js/gallery.js",
        "/js/pages/note.js", "/images/avatar-default.svg", "/vendor/bootstrap/bootstrap.min.css",
        "/favicon.ico"}) {
      var uri = URI.create("http://localhost:" + port + path);
      var first = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, first.statusCode(), path);
      String cache = first.headers().firstValue("Cache-Control").orElseThrow();
      assertTrue(cache.contains("public") && cache.contains("no-cache") && !cache.contains("no-store"), cache);
      String modified = first.headers().firstValue("Last-Modified").orElseThrow();
      var cached = client.send(HttpRequest.newBuilder(uri).header("If-Modified-Since", modified)
          .GET().build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(304, cached.statusCode(), path);
      assertEquals("", cached.body());
    }
    for (String path : new String[] {"/", "/js/missing.js"}) {
      var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
          .GET().build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(path.equals("/") ? 200 : 404, response.statusCode());
      assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    }
  }
}
