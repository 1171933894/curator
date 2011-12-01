/*
 *
 *  Copyright 2011 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.netflix.curator.x.discovery;

import com.netflix.curator.x.discovery.details.InstanceProvider;

/**
 * A strategy for picking one from a set of instances
 */
public interface ProviderStrategy<T>
{
    /**
     * Given a source of instances, return one of them for a single use.
     *
     * @param instanceProvider the instance provider
     * @return the instance to use
     * @throws Exception any errors
     */
    public ServiceInstance<T>       getInstance(InstanceProvider<T> instanceProvider) throws Exception;
}
