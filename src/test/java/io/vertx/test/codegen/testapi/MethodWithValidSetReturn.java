package io.vertx.test.codegen.testapi;

import io.vertx.core.gen.VertxGen;

import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface MethodWithValidSetReturn {

  Set<Byte> byteSet();
  Set<Short> shortSet();
  Set<Integer> intSet();
  Set<Long> longSet();
  Set<Float> floatSet();
  Set<Double> doubleSet();
  Set<Boolean> booleanSet();
  Set<Character> charSet();
  Set<String> stringSet();

  Set<VertxGenClass1> vertxGen1Set();
  Set<VertxGenClass2> vertxGen2Set();


}
