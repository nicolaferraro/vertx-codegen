package io.vertx.codegen.type;

import io.vertx.codegen.ClassModel;
import io.vertx.codegen.Helper;
import io.vertx.codegen.ModuleInfo;
import io.vertx.codegen.TypeParamInfo;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Type info factory based on <i>javax.lang.model</i> and type mirrors.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TypeMirrorFactory {

  final Elements elementUtils;
  final Types typeUtils;

  public TypeMirrorFactory(Elements elementUtils, Types typeUtils) {
    this.elementUtils = elementUtils;
    this.typeUtils = typeUtils;
  }

  public TypeInfo create(TypeMirror type) {
    return create(null, type);
  }

  public TypeInfo create(TypeUse use, TypeMirror type) {
    switch (type.getKind()) {
      case VOID:
        return VoidTypeInfo.INSTANCE;
      case ERROR:
      case DECLARED:
        return create(use, (DeclaredType) type);
      case DOUBLE:
      case LONG:
      case FLOAT:
      case CHAR:
      case BYTE:
      case SHORT:
      case BOOLEAN:
      case INT:
        if (use != null && use.isNullable()) {
          throw new IllegalArgumentException("Primitive types cannot be annotated with @Nullable");
        }
        return PrimitiveTypeInfo.PRIMITIVES.get(type.getKind().name().toLowerCase());
      case TYPEVAR:
        return create(use, (TypeVariable) type);
      default:
        throw new IllegalArgumentException("Illegal type " + type + " of kind " + type.getKind());
    }
  }

  public TypeInfo create(DeclaredType type) {
    return create(null, type);
  }

  public TypeInfo create(TypeUse use, DeclaredType type) {
    boolean nullable = use != null && use.isNullable();
    TypeElement elt = (TypeElement) type.asElement();
    PackageElement pkgElt = elementUtils.getPackageOf(elt);
    ModuleInfo module = ModuleInfo.resolve(elementUtils, pkgElt);
    String fqcn = elt.getQualifiedName().toString();
    boolean proxyGen = elt.getAnnotation(ProxyGen.class) != null;
    if (elt.getKind() == ElementKind.ENUM) {
      ArrayList<String> values = new ArrayList<>();
      for (Element enclosedElt : elt.getEnclosedElements()) {
        if (enclosedElt.getKind() == ElementKind.ENUM_CONSTANT) {
          values.add(enclosedElt.getSimpleName().toString());
        }
      }
      boolean gen = elt.getAnnotation(VertxGen.class) != null;
      return new EnumTypeInfo(fqcn, gen, values, module, nullable, proxyGen);
    } else {
      ClassKind kind = ClassKind.getKind(fqcn, elt.getAnnotation(DataObject.class) != null, elt.getAnnotation(VertxGen.class) != null);
      ClassTypeInfo raw;
      if (kind == ClassKind.BOXED_PRIMITIVE) {
        raw = ClassTypeInfo.PRIMITIVES.get(fqcn);
        if (nullable) {
          raw = new ClassTypeInfo(raw.kind, raw.name, raw.module, true, raw.params);
        }
      } else {
        List<TypeParamInfo.Class> typeParams = createTypeParams(type);
        if (kind == ClassKind.API) {
          VertxGen genAnn = elt.getAnnotation(VertxGen.class);
          TypeInfo[] args = Stream.of(
              ClassModel.VERTX_READ_STREAM,
              ClassModel.VERTX_WRITE_STREAM,
              ClassModel.VERTX_HANDLER
          ).map(s -> {
            TypeElement parameterizedElt = elementUtils.getTypeElement(s);
            TypeMirror parameterizedType = parameterizedElt.asType();
            TypeMirror rawType = typeUtils.erasure(parameterizedType);
            if (typeUtils.isSubtype(type, rawType)) {
              TypeMirror resolved = Helper.resolveTypeParameter(typeUtils, type, parameterizedElt.getTypeParameters().get(0));
              if (resolved.getKind() == TypeKind.DECLARED) {
                DeclaredType dt = (DeclaredType) resolved;
                TypeElement a = (TypeElement) dt.asElement();
                if (a.getQualifiedName().toString().equals("io.vertx.core.AsyncResult")) {
                  return null;
                }
              }
              return create(resolved);
            }
            return null;
          }).toArray(TypeInfo[]::new);
          raw = new ApiTypeInfo(fqcn, genAnn.concrete(), typeParams, args[0], args[1], args[2], module, nullable, proxyGen);
        } else if (kind == ClassKind.DATA_OBJECT) {
          boolean _abstract = elt.getModifiers().contains(Modifier.ABSTRACT);
          raw = new DataObjectTypeInfo(kind, fqcn, module, _abstract, nullable, proxyGen, typeParams);
        } else {
          raw = new ClassTypeInfo(kind, fqcn, module, nullable, typeParams);
        }
      }
      List<? extends TypeMirror> typeArgs = type.getTypeArguments();
      if (typeArgs.size() > 0) {
        List<TypeInfo> typeArguments;
        typeArguments = new ArrayList<>(typeArgs.size());
        for (int i = 0; i < typeArgs.size(); i++) {
          TypeUse argUse = use != null ? use.getArg(i) : null;
          TypeInfo typeArgDesc = create(argUse, typeArgs.get(i));
          // Need to check it is an interface type
          typeArguments.add(typeArgDesc);
        }
        return new ParameterizedTypeInfo(raw, nullable, typeArguments);
      } else {
        return raw;
      }
    }
  }

  public TypeVariableInfo create(TypeUse use, TypeVariable type) {
    TypeParameterElement elt = (TypeParameterElement) type.asElement();
    TypeParamInfo param = TypeParamInfo.create(elt);
    return new TypeVariableInfo(param, use != null && use.isNullable(), type.toString());
  }

  private List<TypeParamInfo.Class> createTypeParams(DeclaredType type) {
    List<TypeParamInfo.Class> typeParams = new ArrayList<>();
    TypeElement elt = (TypeElement) type.asElement();
    List<? extends TypeParameterElement> typeParamElts = elt.getTypeParameters();
    for (int index = 0; index < typeParamElts.size(); index++) {
      TypeParameterElement typeParamElt = typeParamElts.get(index);
      typeParams.add(new TypeParamInfo.Class(elt.getQualifiedName().toString(), index, typeParamElt.getSimpleName().toString()));
    }
    return typeParams;
  }
}
