/**
z * Copyright 2010-2013 Scale Unlimited.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.scaleunlimited.cascading;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DatumCompiler {

    public static class CompiledDatum {
        private String _packageName;
        private String _className;
        private String _classCode;
        
        public CompiledDatum(String packageName, String className, String classCode) {
            _packageName = packageName;
            _className = className;
            _classCode = classCode;
        }

        public String getPackageName() {
            return _packageName;
        }

        public String getClassName() {
            return _className;
        }

        public String getClassCode() {
            return _classCode;
        }
    }
    
    private static final Pattern ARRAY_TYPE_PATTERN = Pattern.compile("\\[L(.+);");
    
    private static final String FILE_HEADER = "/**\n" 
                    + " * Autogenerated by Scale Unlimited's DatumCompiler\n" 
                    + " * \n" 
                    + " * DO NOT EDIT DIRECTLY\n"
                    + " * SUB-CLASS TO CUSTOMIZE\n" 
                    + " */\n\n";

    public static CompiledDatum generate(Class clazz) {

        StringBuilder result = new StringBuilder(FILE_HEADER);

        String packageName = clazz.getPackage().getName();
        header(result, packageName);

        // public class xxx {
        // TODO make it abstract is clazz is abstract
        String className = makeClassName(clazz.getSimpleName());
        line(result, 0, "public class " + className + " extends BaseDatum {");
        line(result, 0, "");

        // public static final String URL_FN = fieldName(FetchedDatum.class, "url");

        List<String> fieldNames = new ArrayList<String>();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            String cleanedName = stripLeadingUnderscores(field.getName());
            String fieldNameConstant = makeFieldNameConstant(cleanedName);

            fieldNames.add(fieldNameConstant);

            line(result, 1, "public static final String " + fieldNameConstant + " = fieldName(" + className + ".class, \"" + cleanedName + "\");");
        }

        line(result, 1, "");

        // public static final Fields FIELDS = new Fields(new String[] {URL_FN, ...});
        // FUTURE put first field on first line.
        // FUTURE only use multiple lines if result is > some max length.
        line(result, 1, "public static final Fields FIELDS = new Fields(new String[] {");
        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            line(result, 13, fieldName + (i == fieldNames.size() - 1 ? "});" : ","));
        }

        line(result, 1, "");

        // public MyDatum() {
        result.append("\tpublic ");
        result.append(className);
        result.append("() {\n");
        result.append("\t\tsuper(FIELDS);\n");
        result.append("\t}\n");

        result.append("\n");

        // public MyDatum(Tuple tuple) {
        result.append("\tpublic ");
        result.append(className);
        result.append("(Tuple tuple) {\n");
        result.append("\t\tsuper(FIELDS, tuple);\n");
        result.append("\t}\n");

        result.append("\n");

        // public MyDatum(TupleEntry tupleEntry) {
        result.append("\tpublic ");
        result.append(className);
        result.append("(TupleEntry tupleEntry) {\n");
        result.append("\t\tsuper(tupleEntry);\n");
        result.append("\t\tvalidateFields(tupleEntry, FIELDS);\n");
        result.append("\t}\n");

        result.append("\n");

        // public MyDatum(String name, int age) {
        result.append("\tpublic ");
        result.append(className);
        result.append("(");

        // For each parameter (field)
        boolean firstField = true;
        for (Field field : fields) {
            if (firstField) {
                firstField = false;
            } else {
                result.append(", ");
            }

            result.append(cleanTypeName(field));
            result.append(' ');
            result.append(stripLeadingUnderscores(field.getName()));
        }

        result.append(") {\n");
        result.append("\t\tsuper(FIELDS);\n");
        result.append("\n");

        // For each parameter (field), make a setXXX() call
        for (Field field : fields) {
            String fieldName = stripLeadingUnderscores(field.getName());
            line(result, 2, makeGetSetFunctionName(fieldName, "set") + "(" + fieldName + ");");
        }

        result.append("\t}\n");
        line(result, 1, "");

        // For each parameter (field), define getXXX and setXXX methods.
        // TODO if field type is transient, set up abstract get/set (and check that
        // class is abstract).
        for (Field field : fields) {
            String fieldName = stripLeadingUnderscores(field.getName());
            String fieldNameConstant = makeFieldNameConstant(fieldName);
            String typeName = cleanTypeName(field);
            boolean isEnum = isEnumType(field);
            boolean isDate = field.getType() == Date.class;
            boolean isUUID = field.getType() == UUID.class;
            
            line(result, 1, "public void " + makeGetSetFunctionName(fieldName, "set") + "(" + typeName + " " + fieldName + ") {");
            // TODO use set<type> setters where appropriate.
            if (isArrayType(field)) {
                line(result, 2, "_tupleEntry.setObject(" + fieldNameConstant + ", makeTupleFromList(" + fieldName + ");");
            } else if (isEnum) {
                line(result, 2, "_tupleEntry.setInteger(" + fieldNameConstant + ", " + fieldName + ".ordinal());");
            } else if (isDate) {
                line(result, 2, "_tupleEntry.setLong(" + fieldNameConstant + ", " + fieldName + ".getTime());");
            } else if (isUUID) {
                line(result, 2, "_tupleEntry.setString(" + fieldNameConstant + ", new UUIDWritable(" + fieldName + "));");
            } else if ((field.getType() == int.class) || (field.getType() == Integer.class)) {
                line(result, 2, "_tupleEntry.setInteger(" + fieldNameConstant + ", " + fieldName + ");");
            } else if ((field.getType() == long.class) || (field.getType() == Long.class)) {
                line(result, 2, "_tupleEntry.setLong(" + fieldNameConstant + ", " + fieldName + ");");
            } else {
                line(result, 2, "_tupleEntry.set(" + fieldNameConstant + ", " + fieldName + ");");
            }
            
            line(result, 1, "}");
            line(result, 1, "");

            line(result, 1, "public " + typeName + " " + makeGetSetFunctionName(fieldName, "get") + "() {");
            
            if (isEnum) {
                // We saved the ordinal value, so covert back to enum
                line(result, 2, "return " + typeName + ".values()[_tupleEntry.getInteger(" + fieldNameConstant + ")];");
            } else if (isDate) {
                // We saved the time as a long, so covert back to Date
                line(result, 2, "return new java.util.Date(_tupleEntry.getLong(" + fieldNameConstant + "));");
            } else if (isUUID) {
                // We saved the UUID as a UUIDWritable, so covert back to UUID
                line(result, 2, "return ((com.scaleunlimited.cascading.UUIDWritable)_tupleEntry.getObject(" + fieldNameConstant + ")).getUUID();");
            } else {
                String cascadingGetter = mapFieldTypeToGetter(typeName);
                String resultCast = (cascadingGetter.equals("Object") ? "(" + typeName + ")" : "");
                line(result, 2, "return " + resultCast + "_tupleEntry.get" + cascadingGetter + "(" + fieldNameConstant + ");");
            }
            
            line(result, 1, "}");
            line(result, 1, "");
        }

        line(result, 0, "}");

        return new CompiledDatum(packageName, className, result.toString());
    }


    private static boolean isArrayType(Field field) {
        // TODO return true if it's an array
        return false;
    }
    
    private static boolean isEnumType(Field field) {
        Class clazz = field.getType();
        Object[] enums = clazz.getEnumConstants();
        return enums != null;
    }
    
    private static String mapFieldTypeToGetter(String typeName) {
        if (typeName.equals("boolean") || typeName.equals("Boolean")) {
            return "Boolean";
        } else if (typeName.equals("short") || typeName.equals("Short")) {
            return "Short";
        } else if (typeName.equals("int") || typeName.equals("Integer")) {
            return "Integer";
        } else if (typeName.equals("long") || typeName.equals("Long")) {
            return "Long";
        } else if (typeName.equals("float") || typeName.equals("Float")) {
            return "Float";
        } else if (typeName.equals("double") || typeName.equals("Double")) {
            return "Double";
        } else if (typeName.equals("String")) {
            return "String";
        } else {
            return "Object";
        }
    }

    //    public static final String BASE_DATUM_CLASSNAME = "BaseDatum";
    //
    //    private final Set<Schema> _queue = new HashSet<Schema>();
    //
    //    /*
    //     * List of Java reserved words from
    //     * http://java.sun.com/docs/books/jls/third_edition/html/lexical.html.
    //     */
    //    private static final Set<String> RESERVED_WORDS = new HashSet<String>(Arrays.asList(new String[] { "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    //                    "continue", "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
    //                    "interface", "long", "native", "new", "null", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    //                    "throw", "throws", "transient", "true", "try", "void", "volatile", "while" }));
    //    
    //
    //    public DatumCompiler(Schema schema) {
    //        enqueue(schema);
    //    }
    //
    //    /**
    //     * Captures output file path and contents.
    //     */
    //    static class OutputFile {
    //        String path;
    //        String contents;
    //
    //        /**
    //         * Writes output to path destination directory when it is newer than
    //         * src, creating directories as necessary. Returns the created file.
    //         */
    //        File writeToDestination(File src, File destDir) throws IOException {
    //            File f = new File(destDir, path);
    //            if (src != null && f.exists() && f.lastModified() >= src.lastModified())
    //                return f; // already up to date: ignore
    //            f.getParentFile().mkdirs();
    //            FileWriter fw = new FileWriter(f);
    //            try {
    //                fw.write(FILE_HEADER);
    //                fw.write(contents);
    //            } finally {
    //                fw.close();
    //            }
    //            return f;
    //        }
    //    }
    //
    //    /** Generates Java classes for a schema. */
    //    public static void compileSchema(String superDatum, File src, File dest) throws IOException {
    //        Schema schema = Schema.parse(src);
    //        if (schema.isError()) {
    //            throw new RuntimeException("Error schemas are not supported: " + schema);
    //        }
    //
    //        DatumCompiler compiler = new DatumCompiler(schema);
    //        compiler.compileToDestination(superDatum, src, dest);
    //    }
    //

    public static String makeClassName(String className) {
        return className.replaceFirst("DatumTemplate$", "Datum");
    }

    public static String makeFieldNameConstant(String fieldName) {
        StringBuilder result = new StringBuilder();

        boolean inUpper = false;
        boolean inAcronym = false;

        for (char c : fieldName.toCharArray()) {
            boolean isUpper = Character.isUpperCase(c);
            if (isUpper != inUpper) {
                if (isUpper || (inUpper && inAcronym)) {
                    result.append('_');
                }

                inAcronym = false;
            } else if (isUpper) {
                inAcronym = true;
            }

            inUpper = isUpper;
            result.append(Character.toUpperCase(c));
        }

        result.append("_FN");
        return result.toString();
    }

    private static String cleanTypeName(Field field) {
        String typeName = field.getType().getName();
        
        // See if we have an array of some type.
        String typeSuffix = "";
        Matcher m = ARRAY_TYPE_PATTERN.matcher(typeName);
        if (m.matches()) {
            typeSuffix = "[]";
            typeName = m.group(1);
        }
        
        // java.lang is included implicitly, so we don't need to qualify it.
        typeName = typeName.replaceFirst("^java.lang.", "");

        // We import Tuple, so it doesn't have to be fully qualified
        typeName = typeName.replaceFirst("^cascading.tuple.Tuple", "Tuple");

        return typeName + typeSuffix;
    }

    private static String stripLeadingUnderscores(String fieldName) {
        return fieldName.replaceFirst("^[_]+", "");
    }

    public static String makeGetSetFunctionName(String fieldName, String prefix) {
        StringBuilder result = new StringBuilder(prefix);

        boolean firstChar = true;
        for (char c : fieldName.toCharArray()) {
            if (firstChar) {
                c = Character.toUpperCase(c);
                firstChar = false;
            }

            result.append(c);
        }

        return result.toString();
    }
    //    
    //    static String mangle(String word) {
    //        if (RESERVED_WORDS.contains(word)) {
    //            return word + "$";
    //        }
    //        return word;
    //    }
    //
    //    /** Recursively enqueue schemas that need a class generated. */
    //    private void enqueue(Schema schema) {
    //        if (_queue.contains(schema))
    //            return;
    //        
    //        switch (schema.getType()) {
    //            case RECORD:
    //                _queue.add(schema);
    //                for (Schema.Field field : schema.getFields()) {
    //                    enqueue(field.schema());
    //                }
    //                break;
    //                
    //            case MAP:
    //                enqueue(schema.getValueType());
    //                break;
    //                
    //            case ARRAY:
    //                enqueue(schema.getElementType());
    //                break;
    //                
    //            case ENUM:
    //                _queue.add(schema);
    //                break;
    //                
    //            case STRING:
    //            case BYTES:
    //            case INT:
    //            case LONG:
    //            case FLOAT:
    //            case DOUBLE:
    //            case BOOLEAN:
    //                break;
    //                
    //            case FIXED:
    //            case UNION:
    //            case NULL:
    //                throw new RuntimeException("Unsupported type: " + schema);
    //
    //            default:
    //                throw new RuntimeException("Unknown type: " + schema);
    //        }
    //}
    //
    //    private void compileToDestination(String superDatum, File src, File dst) throws IOException {
    //        for (Schema schema : _queue) {
    //            OutputFile o = compile(superDatum, schema);
    //            o.writeToDestination(src, dst);
    //        }
    //    }
    //
    //    private static String makePath(String name, String space) {
    //        if (space == null || space.isEmpty()) {
    //            return name + ".java";
    //        } else {
    //            return space.replace('.', File.separatorChar) + File.separatorChar + name + ".java";
    //        }
    //    }
    //
    private static void header(StringBuilder out, String namespace) {
        if (namespace != null) {
            line(out, 0, "package " + namespace + ";\n");
        }

        line(out, 0, "import cascading.tuple.Fields;");
        line(out, 0, "import cascading.tuple.Tuple;");
        line(out, 0, "import cascading.tuple.TupleEntry;");
        line(out, 0, "import com.scaleunlimited.cascading.BaseDatum;");
        line(out, 0, "");
        
        // Janino doesn't support annotations, so we can't use this if we want to test
        // TODO support flag for compilation that says whether to include annotations.
        // line(out, 0, "@SuppressWarnings(\"serial\")");
    }

    private static void doc(StringBuilder out, int indent, String doc) {
        if (doc != null) {
            line(out, indent, "/** " + escapeForJavaDoc(doc) + " */");
        }
    }

    /**
     * Be sure that generated code will compile by replacing end-comment markers
     * with the appropriate HTML entity.
     */
    private static String escapeForJavaDoc(String doc) {
        return doc.replace("*/", "*&#47;");
    }

    //    private String type(Schema schema) {
    //        switch (schema.getType()) {
    //            case ENUM:
    //                return mangle(schema.getFullName());
    //            case ARRAY:
    //                return "java.util.List<" + type(schema.getElementType()) + ">";
    //            case MAP:
    //                return "java.util.Map<String, " + type(schema.getValueType()) + ">";
    //            case STRING:
    //                return "String";
    //            case BYTES:
    //                return "java.nio.ByteBuffer";
    //            case INT:
    //                return "int";
    //            case LONG:
    //                return "long";
    //            case FLOAT:
    //                return "float";
    //            case DOUBLE:
    //                return "double";
    //            case BOOLEAN:
    //                return "boolean";
    //                
    //            case FIXED:
    //            case UNION:
    //            case NULL:
    //                throw new RuntimeException("Unsupported type type: " + schema);
    //
    //            case RECORD:
    //                throw new RuntimeException("Can't have records inside of records: " + schema);
    //
    //            default:
    //                throw new RuntimeException("Unknown type: " + schema);
    //        }
    //    }
    //
    //    private String unbox(Schema schema) {
    //        switch (schema.getType()) {
    //            case INT:
    //            return "int";
    //            case LONG:
    //            return "long";
    //            case FLOAT:
    //            return "float";
    //            case DOUBLE:
    //            return "double";
    //            case BOOLEAN:
    //            return "boolean";
    //            default:
    //            return type(schema);
    //        }
    //    }
    //
    private static void line(StringBuilder out, int indent, String text) {
        for (int i = 0; i < indent; i++) {
            out.append("    ");
        }
        out.append(text);
        out.append("\n");
    }

    private static String esc(Object o) {
        return o.toString().replace("\"", "\\\"");
    }
}
