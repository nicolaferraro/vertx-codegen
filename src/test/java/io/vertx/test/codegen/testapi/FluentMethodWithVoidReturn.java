package io.vertx.test.codegen.testapi;

import io.vertx.core.gen.Fluent;
import io.vertx.core.gen.VertxGen;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface FluentMethodWithVoidReturn {

  @Fluent
  void foo(String str);
}
