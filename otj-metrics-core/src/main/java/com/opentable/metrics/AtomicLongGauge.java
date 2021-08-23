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
package com.opentable.metrics;

import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.Counting;
import com.codahale.metrics.Gauge;

/** A fine cross-breed of an AtomicLong and a Gauge&lt;Long&gt; */
public class AtomicLongGauge extends AtomicLong implements Gauge<Long>, Counting {
    private static final long serialVersionUID = 1L;

    @Override
    public Long getValue() {
        return get();
    }

    @Override
    public long getCount() {
        return get();
    }
}
