/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opentable.metrics.ready;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A ReadyCheck check for a component of your application.
 * A much simplified version of the Codahale Healthcheck
 */
public abstract class ReadyCheck {
    /**
     * The result of a {@link ReadyCheck} being run. It can be ready (with an optional message and optional details)
     * or unready (with either an error message or a thrown exception and optional details).
     */
    public static class Result {
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

        private final boolean ready;
        private final String message;
        private final Throwable error;
        private final String timestamp =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(ZonedDateTime.now());

        private Result(boolean isReady, String message, Throwable error) {
            this.ready = isReady;
            this.message = message;
            this.error = error;
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
            result = PRIME * result + timestamp.hashCode();
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
            builder.append('}');
            return builder.toString();
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
