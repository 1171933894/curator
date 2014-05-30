package org.apache.curator.x.rpc.idl.event;

import com.facebook.swift.codec.ThriftEnum;

@ThriftEnum("PersistentEphemeralNodeMode")
public enum RpcPersistentEphemeralNodeMode
{
    EPHEMERAL,
    EPHEMERAL_SEQUENTIAL,
    PROTECTED_EPHEMERAL,
    PROTECTED_EPHEMERAL_SEQUENTIAL
}
