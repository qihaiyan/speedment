/**
 *
 * Copyright (c) 2006-2016, Speedment, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); You may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.speedment.generator.internal.entity;

import com.speedment.common.codegen.model.Class;
import com.speedment.common.codegen.model.Constructor;
import com.speedment.common.codegen.model.Field;
import com.speedment.common.codegen.model.File;
import com.speedment.common.codegen.model.Generic;
import com.speedment.common.codegen.model.Import;
import com.speedment.common.codegen.model.Method;
import com.speedment.common.codegen.model.Type;
import com.speedment.runtime.config.Table;
import static com.speedment.common.codegen.internal.model.constant.DefaultAnnotationUsage.OVERRIDE;
import static com.speedment.common.codegen.internal.model.constant.DefaultType.BOOLEAN_PRIMITIVE;
import static com.speedment.common.codegen.internal.model.constant.DefaultType.INT_PRIMITIVE;
import static com.speedment.common.codegen.internal.model.constant.DefaultType.OBJECT;
import static com.speedment.common.codegen.internal.model.constant.DefaultType.OPTIONAL;
import static com.speedment.common.codegen.internal.model.constant.DefaultType.STRING;
import static com.speedment.common.codegen.internal.util.Formatting.nl;
import static com.speedment.common.codegen.internal.util.Formatting.tab;
import static com.speedment.generator.internal.DefaultJavaClassTranslator.GETTER_METHOD_PREFIX;
import static com.speedment.generator.internal.DefaultJavaClassTranslator.SETTER_METHOD_PREFIX;
import com.speedment.generator.internal.EntityAndManagerTranslator;
import com.speedment.runtime.config.Column;
import com.speedment.runtime.internal.entity.AbstractEntity;
import java.util.Objects;
import java.util.StringJoiner;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 *
 * @author Emil Forslund
 * @author Per-Åke Minborg
 */
public final class GeneratedEntityImplTranslator extends EntityAndManagerTranslator<Class> {

    public GeneratedEntityImplTranslator(Table table) {
        super(table, Class::of);
    }

    @Override
    protected Class makeCodeGenModel(File file) {
        requireNonNull(file);

        return newBuilder(file, getSupport().generatedEntityImplName())
            /**
             * Getters
             */
            .forEveryColumn((clazz, col) -> {
                final Type retType;
                final String getter;
                if (col.isNullable()) {
                    retType = OPTIONAL.add(Generic.of().add(Type.of(col.findTypeMapper().getJavaType())));
                    getter = "Optional.ofNullable(" + getSupport().variableName(col) + ")";
                } else {
                    retType = Type.of(col.findTypeMapper().getJavaType());
                    getter = getSupport().variableName(col);
                }
                clazz
                    .add(fieldFor(col).private_())
                    .add(Method.of(GETTER_METHOD_PREFIX + getSupport().typeName(col), retType)
                        .public_()
                        .add(OVERRIDE)
                        .add("return " + getter + ";"));

            })
            /**
             * Setters
             */
            .forEveryColumn((clazz, col) -> {
                clazz
                    .add(Method.of(SETTER_METHOD_PREFIX + getSupport().typeName(col), getSupport().entityType())
                        .public_().final_()
                        .add(OVERRIDE)
                        .add(fieldFor(col))
                        .add("this." + getSupport().variableName(col) + " = " + getSupport().variableName(col) + ";")
                        .add("return this;"));
            })
            /**
             * Class details
             */
            .forEveryTable(Phase.POST_MAKE, (clazz, table) -> {
                clazz
                    .add(copy(file))
                    .add(toString(file))
                    .add(equalsMethod())
                    .add(hashCodeMethod())
                    .add(Method.of("entityClass", Type.of(java.lang.Class.class).add(Generic.of().add(getSupport().entityType()))).public_().add(OVERRIDE)
                        .add("return " + getSupport().entityName() + ".class;")
                    );
            })
            .build()
            .public_()
            .abstract_()
            .setSupertype(Type.of(AbstractEntity.class).add(Generic.of().add(getSupport().entityType())))
            .add(getSupport().entityType())
            .add(Constructor.of().protected_());

    }
    
    protected Method copy(File file) {
        file.add(Import.of(getSupport().entityImplType()));
        final Method result = Method.of("copy", getSupport().entityType())
            .add(OVERRIDE).public_()
            .add("return new " + getSupport().entityImplName() + "()");
        
        result.add(
            getDocument().columns()
                .map(Column::getJavaName)
                .map(c -> ".set" + getNamer().javaTypeName(c) + "(" + getNamer().javaVariableName(c) + ")")
                .collect(joining(nl() + tab(), tab(), ";"))
                .split(nl())
        );
        
        return result;
    }

    protected Method toString(File file) {
        file.add(Import.of(Type.of(StringJoiner.class)));
        file.add(Import.of(Type.of(Objects.class)));
        final Method m = Method.of("toString", STRING)
            .public_()
            .add(OVERRIDE)
            .add("final StringJoiner sj = new StringJoiner(\", \", \"{ \", \" }\");");

        columns().forEachOrdered(c -> {
            final String getter;
            if (c.isNullable()) {
                getter = "get" + getSupport().typeName(c) + "()" + ".orElse(null)";
            } else {
                getter = "get" + getSupport().typeName(c) + "()";
            }
            m.add("sj.add(\"" + getSupport().variableName(c) + " = \" + Objects.toString(" + getter + "));");
        });

        m.add("return \"" + getSupport().entityImplName() + " \" + sj.toString();");

        return m;

    }

    private Method equalsMethod() {

        final String thatName = "that";
        final String thatCastedName = thatName + getSupport().entityName();
        final Method method = Method.of("equals", BOOLEAN_PRIMITIVE)
            .public_()
            .add(OVERRIDE)
            .add(Field.of(thatName, OBJECT))
            .add("if (this == that) { return true; }")
            .add("if (!(" + thatName + " instanceof " + getSupport().entityName() + ")) { return false; }")
            .add("final " + getSupport().entityName() + " " + thatCastedName + " = (" + getSupport().entityName() + ")" + thatName + ";");

        columns().forEachOrdered(c -> {
            final String getter = "get" + getSupport().typeName(c);
            if (c.findTypeMapper().getJavaType().isPrimitive()) {
                method.add("if (this." + getter + "() != " + thatCastedName + "." + getter + "()) {return false; }");
            } else {
                method.add("if (!Objects.equals(this." + getter + "(), " + thatCastedName + "." + getter + "())) {return false; }");
            }
        });

        method.add("return true;");
        return method;
    }

    private Method hashCodeMethod() {
        final Method method = Method.of("hashCode", INT_PRIMITIVE)
            .public_()
            .add(OVERRIDE)
            .add("int hash = 7;");

        columns().forEachOrdered(c -> {

            final StringBuilder str = new StringBuilder();
            str.append("hash = 31 * hash + ");

            switch (c.findTypeMapper().getJavaType().getName()) {
                case "byte":
                    str.append("Byte");
                    break;
                case "short":
                    str.append("Short");
                    break;
                case "int":
                    str.append("Integer");
                    break;
                case "long":
                    str.append("Long");
                    break;
                case "float":
                    str.append("Float");
                    break;
                case "double":
                    str.append("Double");
                    break;
                case "boolean":
                    str.append("Boolean");
                    break;
                case "char":
                    str.append("Character");
                    break;
                default:
                    str.append("Objects");
                    break;
            }

            str.append(".hashCode(get").append(getSupport().typeName(c)).append("());");
            method.add(str.toString());
        });

        method.add("return hash;");
        return method;
    }

    @Override
    protected String getJavadocRepresentText() {
        return "The generated base implementation";
    }

    @Override
    protected String getClassOrInterfaceName() {
        return getSupport().generatedEntityImplName();
    }

    @Override
    public boolean isInGeneratedPackage() {
        return true;
    }
}
