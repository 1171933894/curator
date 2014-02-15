/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.curator.x.rest.entities;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DeleteSpec
{
    private String path;
    private boolean async;
    private String asyncId;
    private boolean guaranteed;
    private int version;

    public DeleteSpec()
    {
        this("/", false, "", false, -1);
    }

    public DeleteSpec(String path, boolean async, String asyncId, boolean guaranteed, int version)
    {
        this.path = path;
        this.async = async;
        this.asyncId = asyncId;
        this.guaranteed = guaranteed;
        this.version = version;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public boolean isAsync()
    {
        return async;
    }

    public void setAsync(boolean async)
    {
        this.async = async;
    }

    public String getAsyncId()
    {
        return asyncId;
    }

    public void setAsyncId(String asyncId)
    {
        this.asyncId = asyncId;
    }

    public boolean isGuaranteed()
    {
        return guaranteed;
    }

    public void setGuaranteed(boolean guaranteed)
    {
        this.guaranteed = guaranteed;
    }

    public int getVersion()
    {
        return version;
    }

    public void setVersion(int version)
    {
        this.version = version;
    }
}
