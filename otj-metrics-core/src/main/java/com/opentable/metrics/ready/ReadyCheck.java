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

/**
 * A ReadyCheck check for a component of your application.
 * A much simplified version of the Codahale Healthcheck
 */
public abstract class ReadyCheck {

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
