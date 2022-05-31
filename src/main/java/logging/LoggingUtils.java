package logging;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingUtils {
    private LoggingUtils() {}

    public static <T, E> void logMap(Logger logger, Map<T, E> map) {
        for(Map.Entry<T, E> entry : map.entrySet()) {
            logger.log(Level.INFO, entry.getKey().toString() + ": " + entry.getValue().toString());
        }
    }

}
