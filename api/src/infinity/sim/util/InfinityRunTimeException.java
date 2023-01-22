package infinity.sim.util;

public class InfinityRunTimeException extends RuntimeException {

    public InfinityRunTimeException(final String message) {
        super(message);
    }

    public InfinityRunTimeException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public InfinityRunTimeException(final Throwable cause) {
        super(cause);
    }

    public InfinityRunTimeException() {
        super();
    }
}
