package tomocomd;

public class AExOpDCSException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final ExceptionType type;

    public AExOpDCSException(ExceptionType type) {
        super(type.getMessage());
        this.type = type;
    }

    public AExOpDCSException(ExceptionType type, Throwable cause) {
        super(type.getMessage(), cause);
        this.type = type;
    }

    public AExOpDCSException(
            ExceptionType type,
            Throwable cause,
            String message) {
        super(type.formatMessage(message), cause);
        this.type = type;
    }

    public AExOpDCSException(ExceptionType type, String message) {
        super(type.formatMessage(message));
        this.type = type;
    }

    public ExceptionType getType() {
        return type;
    }

    public enum ExceptionType {
        CSV_FILE_WRITING_EXCEPTION("Error writing csv file"),
        CSV_FILE_LOADING_EXCEPTION("Error reading csv file");

        private final String message;

        ExceptionType(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public String formatMessage(String message) {
            return String.format("%s: %s", this.message, message);
        }

        public AExOpDCSException get() {
            return new AExOpDCSException(this);
        }

        public AExOpDCSException get(String message) {
            return new AExOpDCSException(this, message);
        }

        public AExOpDCSException get(Throwable cause) {
            return new AExOpDCSException(this, cause);
        }

        public AExOpDCSException get(String message, Throwable cause) {
            return new AExOpDCSException(this, cause, message);
        }
    }
}
