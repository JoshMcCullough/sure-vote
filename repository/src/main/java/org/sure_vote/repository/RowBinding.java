package org.sure_vote.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.sure_vote.entity.Identifiable;
import org.sure_vote.entity.WireValue;

final class RowBinding {
    private RowBinding() {
    }

    static String table(final Class<?> type) {
        return "\"" + snake(type.getSimpleName()) + "\"";
    }

    static List<String> tables(final List<Class<?>> types) {
        final var names = new ArrayList<String>();
        for (final var type : types) {
            names.add(table(type));
        }
        return names;
    }

    static String insertSql(final Class<?> type) {
        final var fields = fields(type);
        final var columns = new ArrayList<String>();
        final var marks = new ArrayList<String>();
        collectColumns(columns, marks, fields);
        return insertText(type, columns, marks);
    }

    static String updateSql(final Class<?> type) {
        final var fields = fields(type);
        final var assignments = new ArrayList<String>();
        collectAssignments(assignments, fields);
        return updateText(type, assignments);
    }

    static String findSql(final Class<?> type) {
        return "select * from " + table(type) + " where \"id\" = ?";
    }

    static String listSql(final Class<?> type) {
        return "select * from " + table(type) + " order by \"created_at\", \"id\"";
    }

    static void bindInsert(final PreparedStatement statement, final Identifiable row) throws SQLException {
        final var fields = fields(row.getClass());
        var index = 1;
        for (final var field : fields) {
            bind(statement, index, field, row);
            index = index + 1;
        }
    }

    static void bindUpdate(final PreparedStatement statement, final Identifiable row) throws SQLException {
        final var fields = fields(row.getClass());
        var index = 1;
        for (final var field : fields) {
            index = bindUpdateField(statement, index, field, row);
        }
        statement.setObject(index, row.getId());
    }

    static <T> T read(final ResultSet result, final Class<T> type) throws SQLException {
        final var row = construct(type);
        final var fields = fields(type);
        for (final var field : fields) {
            readField(result, row, field);
        }
        return row;
    }

    private static void collectColumns(
        final List<String> columns,
        final List<String> marks,
        final List<Field> fields
    ) {
        for (final var field : fields) {
            columns.add(quote(column(field)));
            marks.add("?");
        }
    }

    private static String insertText(final Class<?> type, final List<String> columns, final List<String> marks) {
        final var joinedColumns = String.join(", ", columns);
        final var joinedMarks = String.join(", ", marks);
        return "insert into " + table(type) + " (" + joinedColumns + ") values (" + joinedMarks + ")";
    }

    private static void collectAssignments(final List<String> assignments, final List<Field> fields) {
        for (final var field : fields) {
            addAssignment(assignments, field);
        }
    }

    private static String updateText(final Class<?> type, final List<String> assignments) {
        final var joined = String.join(", ", assignments);
        return "update " + table(type) + " set " + joined + " where \"id\" = ?";
    }

    private static void addAssignment(final List<String> assignments, final Field field) {
        if (!"id".equals(field.getName())) {
            assignments.add(quote(column(field)) + " = ?");
        }
    }

    private static int bindUpdateField(
        final PreparedStatement statement,
        final int index,
        final Field field,
        final Identifiable row
    ) throws SQLException {
        if ("id".equals(field.getName())) {
            return index;
        }
        else {
            bind(statement, index, field, row);
            return index + 1;
        }
    }

    private static void bind(
        final PreparedStatement statement,
        final int index,
        final Field field,
        final Identifiable row
    ) throws SQLException {
        final var value = readField(field, row);
        final var stored = stored(value);
        write(statement, index, field, stored);
    }

    private static void write(
        final PreparedStatement statement,
        final int index,
        final Field field,
        final Object stored
    ) throws SQLException {
        if (stored == null) {
            statement.setObject(index, null);
        }
        else if (field.getType() == int.class) {
            final var number = (Integer) stored;
            statement.setInt(index, number.intValue());
        }
        else if (field.getType() == boolean.class) {
            final var flag = (Boolean) stored;
            statement.setBoolean(index, flag.booleanValue());
        }
        else {
            statement.setObject(index, stored);
        }
    }

    private static void readField(final ResultSet result, final Object row, final Field field) throws SQLException {
        final var value = readValue(result, field);
        writeField(field, row, value);
    }

    private static Object readValue(final ResultSet result, final Field field) throws SQLException {
        final var name = column(field);
        if (field.getType() == Instant.class) {
            return instant(result, name);
        }
        else if (field.getType() == LocalDate.class) {
            return result.getObject(name, LocalDate.class);
        }
        else if (field.getType() == UUID.class) {
            return result.getObject(name, UUID.class);
        }
        else if (field.getType() == Integer.class) {
            return result.getObject(name, Integer.class);
        }
        else if (field.getType() == Double.class) {
            return result.getObject(name, Double.class);
        }
        else if (field.getType() == Long.class) {
            return result.getObject(name, Long.class);
        }
        else if (field.getType() == int.class) {
            return Integer.valueOf(result.getInt(name));
        }
        else if (field.getType() == boolean.class) {
            return Boolean.valueOf(result.getBoolean(name));
        }
        else if (enumField(field)) {
            return enumValue(field.getType(), result.getString(name));
        }
        else {
            return result.getString(name);
        }
    }

    private static boolean enumField(final Field field) {
        final var type = field.getType();
        return type.isEnum();
    }

    private static Instant instant(final ResultSet result, final String name) throws SQLException {
        final var offset = result.getObject(name, OffsetDateTime.class);
        if (offset == null) {
            return null;
        }
        else {
            return offset.toInstant();
        }
    }

    private static Object enumValue(final Class<?> type, final String wire) {
        if (wire == null) {
            return null;
        }
        else {
            return matchedEnum(type, wire);
        }
    }

    private static Object matchedEnum(final Class<?> type, final String wire) {
        final var constants = type.getEnumConstants();
        for (final var constant : constants) {
            final var matched = matchWire(constant, wire);
            if (matched != null) {
                return matched;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + wire);
    }

    private static Object matchWire(final Object constant, final String wire) {
        if (constant instanceof WireValue labeled) {
            return matchedLabel(labeled, constant, wire);
        }
        else {
            return null;
        }
    }

    private static Object matchedLabel(final WireValue labeled, final Object constant, final String wire) {
        final var label = labeled.wire();
        if (label.equals(wire)) {
            return constant;
        }
        else {
            return null;
        }
    }

    private static Object stored(final Object value) {
        if (value instanceof WireValue labeled) {
            return labeled.wire();
        }
        else if (value instanceof Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        else {
            return value;
        }
    }

    private static Object readField(final Field field, final Object row) {
        try {
            return field.get(row);
        }
        catch (final IllegalAccessException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private static void writeField(final Field field, final Object row, final Object value) {
        try {
            field.set(row, value);
        }
        catch (final IllegalAccessException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private static <T> T construct(final Class<T> type) {
        try {
            return newInstance(type);
        }
        catch (final ReflectiveOperationException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private static <T> T newInstance(final Class<T> type) throws ReflectiveOperationException {
        final var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return castNew(type, constructor);
    }

    private static <T> T castNew(final Class<T> type, final java.lang.reflect.Constructor<T> constructor)
        throws ReflectiveOperationException {
        final var row = constructor.newInstance();
        return type.cast(row);
    }

    private static List<Field> fields(final Class<?> type) {
        final var found = new ArrayList<Field>();
        collect(found, type);
        return found;
    }

    private static void collect(final List<Field> found, final Class<?> type) {
        final var parent = type.getSuperclass();
        final var declared = type.getDeclaredFields();
        collectParent(found, parent);
        for (final var field : declared) {
            addField(found, field);
        }
    }

    private static void collectParent(final List<Field> found, final Class<?> parent) {
        if (parent != null && parent != Object.class) {
            collect(found, parent);
        }
    }

    private static void addField(final List<Field> found, final Field field) {
        final var modifiers = field.getModifiers();
        final var skipped = Modifier.isStatic(modifiers) || field.isSynthetic();
        if (!skipped) {
            field.setAccessible(true);
            found.add(field);
        }
    }

    private static String column(final Field field) {
        return snake(field.getName());
    }

    private static String snake(final String name) {
        final var text = new StringBuilder();
        final var chars = name.toCharArray();
        for (final var ch : chars) {
            appendSnake(text, ch);
        }
        return withoutLeadingUnderscore(text.toString());
    }

    private static String withoutLeadingUnderscore(final String name) {
        if (name.startsWith("_")) {
            return name.substring(1);
        }
        else {
            return name;
        }
    }

    private static void appendSnake(final StringBuilder text, final char ch) {
        if (Character.isUpperCase(ch)) {
            text.append('_');
            text.append(Character.toLowerCase(ch));
        }
        else {
            text.append(ch);
        }
    }

    private static String quote(final String name) {
        return "\"" + name + "\"";
    }
}
