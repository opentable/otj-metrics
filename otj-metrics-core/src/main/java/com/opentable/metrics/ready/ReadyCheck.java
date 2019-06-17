package com.opentable.metrics.ready;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A ReadyCheck check for a component of your application.
 */
public abstract class ReadyCheck {
    /**
     * The result of a {@link ReadyCheck} being run. It can be ready (with an optional message and optional details)
     * or unready (with either an error message or a thrown exception and optional details).
     */
    public static class Result {
        private static final DateTimeFormatter DATE_FORMAT_PATTERN =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        private static final int PRIME = 31;

        /**
         * Returns a ready {@link Result} with no additional message.
         *
         * @return a ready {@link Result} with no additional message
         */
        public static Result ready() {
            return new Result(true, null, null);
        }

        /**
         * Returns a ready {@link Result} with an additional message.
         *
         * @param message an informative message
         * @return a ready {@link Result} with an additional message
         */
        public static Result ready(String message) {
            return new Result(true, message, null);
        }

        /**
         * Returns a ready {@link Result} with a formatted message.
         * <p/>
         * Message formatting follows the same rules as {@link String#format(String, Object...)}.
         *
         * @param message a message format
         * @param args    the arguments apply to the message format
         * @return a ready {@link Result} with an additional message
         * @see String#format(String, Object...)
         */
        public static Result ready(String message, Object... args) {
            return ready(String.format(message, args));
        }

        /**
         * Returns an unready {@link Result} with the given message.
         *
         * @param message an informative message describing how the ready check failed
         * @return an unready {@link Result} with the given message
         */
        public static Result unready(String message) {
            return new Result(false, message, null);
        }

        /**
         * Returns an unready {@link Result} with a formatted message.
         * <p/>
         * Message formatting follows the same rules as {@link String#format(String, Object...)}.
         *
         * @param message a message format
         * @param args    the arguments apply to the message format
         * @return an unready {@link Result} with an additional message
         * @see String#format(String, Object...)
         */
        public static Result unready(String message, Object... args) {
            return unready(String.format(message, args));
        }

        /**
         * Returns an unready {@link Result} with the given error.
         *
         * @param error an exception thrown during the ready check
         * @return an unready {@link Result} with the given {@code error}
         */
        public static Result unready(Throwable error) {
            return new Result(false, error.getMessage(), error);
        }


        /**
         * Returns a new {@link ResultBuilder}
         *
         * @return the {@link ResultBuilder}
         */
        public static ResultBuilder builder() {
            return new ResultBuilder();
        }

        private final boolean ready;
        private final String message;
        private final Throwable error;
        private final Map<String, Object> details;
        private final String timestamp;

        private Result(boolean isReady, String message, Throwable error) {
            this(isReady, message, error, null);
        }

        private Result(ResultBuilder builder) {
            this(builder.ready, builder.message, builder.error, builder.details);
        }

        private Result(boolean isReady, String message, Throwable error, Map<String, Object> details) {
            this.ready = isReady;
            this.message = message;
            this.error = error;
            this.details = details == null ? null : Collections.unmodifiableMap(details);
            timestamp = DATE_FORMAT_PATTERN.format(ZonedDateTime.now());
        }

        /**
         * Returns {@code true} if the result indicates the component is ready; {@code false}
         * otherwise.
         *
         * @return {@code true} if the result indicates the component is ready
         */
        public boolean isReady() {
            return ready;
        }

        /**
         * Returns any additional message for the result, or {@code null} if the result has no
         * message.
         *
         * @return any additional message for the result, or {@code null}
         */
        public String getMessage() {
            return message;
        }

        /**
         * Returns any exception for the result, or {@code null} if the result has no exception.
         *
         * @return any exception for the result, or {@code null}
         */
        public Throwable getError() {
            return error;
        }

        /**
         * Returns the timestamp when the result was created.
         *
         * @return a formatted timestamp
         */
        public String getTimestamp() {
            return timestamp;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Result result = (Result) o;
            return ready == result.ready &&
                    !(error != null ? !error.equals(result.error) : result.error != null) &&
                    !(message != null ? !message.equals(result.message) : result.message != null) &&
                    !(timestamp != null ? !timestamp.equals(result.timestamp) : result.timestamp != null);
        }

        @Override
        public int hashCode() {
            int result = ready ? 1 : 0;
            result = PRIME * result + (message != null ? message.hashCode() : 0);
            result = PRIME * result + (error != null ? error.hashCode() : 0);
            result = PRIME * result + (timestamp != null ? timestamp.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder("Result{isReady=");
            builder.append(ready);
            if (message != null) {
                builder.append(", message=").append(message);
            }
            if (error != null) {
                builder.append(", error=").append(error);
            }
            builder.append(", timestamp=").append(timestamp);
            if (details != null) {
                for (Map.Entry<String, Object> e : details.entrySet()) {
                    builder.append(", ");
                    builder.append(e.getKey())
                            .append("=")
                            .append(String.valueOf(e.getValue()));
                }
            }
            builder.append('}');
            return builder.toString();
        }
    }

    /**
     * This a convenient builder for an {@link ReadyCheck.Result}. It can be ready (with optional message and detail)
     * or unready (with optional message, error and detail)
     */
    public static class ResultBuilder {
        private boolean ready;
        private String message;
        private Throwable error;
        private Map<String, Object> details;

        protected ResultBuilder() {
            this.ready = true;
            this.details = new LinkedHashMap<>();
        }

        /**
         * Configure an ready result
         *
         * @return this builder with ready status
         */
        public ResultBuilder ready() {
            this.ready = true;
            return this;
        }

        /**
         * Configure an unready result
         *
         * @return this builder with unready status
         */
        public ResultBuilder unready() {
            this.ready = false;
            return this;
        }

        /**
         * Configure an unready result with an {@code error}
         *
         * @param error the error
         * @return this builder with the given error
         */
        public ResultBuilder unready(Throwable error) {
            this.error = error;
            return this.unready().withMessage(error.getMessage());
        }

        /**
         * Set an optional message
         *
         * @param message an informative message
         * @return this builder with the given {@code message}
         */
        public ResultBuilder withMessage(String message) {
            this.message = message;
            return this;
        }

        /**
         * Set an optional formatted message
         * <p/>
         * Message formatting follows the same rules as {@link String#format(String, Object...)}.
         *
         * @param message a message format
         * @param args    the arguments apply to the message format
         * @return this builder with the given formatted {@code message}
         * @see String#format(String, Object...)
         */
        public ResultBuilder withMessage(String message, Object... args) {
            return withMessage(String.format(message, args));
        }

        /**
         * Add an optional detail
         *
         * @param key  a key for this detail
         * @param data an object representing the detail data
         * @return this builder with the given detail added
         */
        public ResultBuilder withDetail(String key, Object data) {
            if (this.details == null) {
                this.details = new LinkedHashMap<>();
            }
            this.details.put(key, data);
            return this;
        }

        public Result build() {
            return new Result(this);
        }
    }

    /**
     * Perform a check of the application component.
     *
     * @return if the component is ready, a ready {@link Result}; otherwise, an unready {@link
     * Result} with a descriptive error message or exception
     * @throws Exception if there is an unhandled error during the ready check; this will result in
     *                   a failed ready check
     */
    protected abstract Result check() throws Exception;

    /**
     * Executes the ready check, catching and handling any exceptions raised by {@link #check()}.
     *
     * @return if the component is ready, a ready {@link Result}; otherwise, an unready {@link
     * Result} with a descriptive error message or exception
     */
    public Result execute() {
        try {
            return check();
        } catch (Exception e) {
            return Result.unready(e);
        }
    }
}
