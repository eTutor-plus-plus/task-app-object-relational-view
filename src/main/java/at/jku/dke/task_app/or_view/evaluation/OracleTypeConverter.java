package at.jku.dke.task_app.or_view.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.Arrays;
import java.util.stream.Collectors;

public class OracleTypeConverter {

    private static final Logger LOG = LoggerFactory.getLogger(OracleTypeConverter.class);

    public static String convertOracleObject(Object val) throws SQLException {
        if (val == null) {
            return "NULL";
        }

        return switch (val) {
            case Struct struct -> convertStruct(struct);
            case Array array -> convertArray(array);
            default -> val.toString();
        };
    }

    private static String convertStruct(Struct struct) throws SQLException {
        Object[] attrs = struct.getAttributes();
        if (attrs == null || attrs.length == 0) {
            return "{}";
        }

        String content = Arrays.stream(attrs)
            .map(attr -> {
                try {
                    return convertOracleObject(attr);
                } catch (SQLException e) {
                    LOG.warn("Error converting struct attribute", e);
                    return "?";
                }
            })
            .collect(Collectors.joining(", "));

        return "{" + content + "}";
    }

    private static String convertArray(Array array) throws SQLException {
        if (!(array.getArray() instanceof Object[] elements)) {
            return array.toString();
        }

        if (elements.length == 0) {
            return "[]";
        }

        String content = Arrays.stream(elements)
            .map(elem -> {
                try {
                    return convertOracleObject(elem);
                } catch (SQLException e) {
                    LOG.warn("Error converting array element", e);
                    return "?";
                }
            })
            .collect(Collectors.joining(", "));

        return "[" + content + "]";
    }
}
