package org.apache.curator.x.rpc.idl.structs;

import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;

@ThriftStruct
public class PersistentEphemeralNodeProjection
{
    @ThriftField(1)
    public String id;

    public PersistentEphemeralNodeProjection()
    {
    }

    public PersistentEphemeralNodeProjection(String id)
    {
        this.id = id;
    }
}
