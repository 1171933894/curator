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
package org.apache.curator.x.async.modeled.details;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import org.apache.curator.utils.ZKPaths;
import org.apache.curator.x.async.modeled.ZPath;
import org.apache.zookeeper.common.PathUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ZPathImpl implements ZPath
{
    public static final ZPath root = new ZPathImpl(Collections.singletonList(ZKPaths.PATH_SEPARATOR), null, null);

    public static final String parameter = "";    // empty paths are illegal so it's useful for this purpose

    private final List<String> nodes;
    private final boolean isResolved;
    private final List<Supplier<Object>> parameterSuppliers;

    public static ZPath parse(String fullPath, UnaryOperator<String> nameFilter)
    {
        List<String> nodes = ImmutableList.<String>builder()
            .add(ZKPaths.PATH_SEPARATOR)
            .addAll(
                Splitter.on(ZKPaths.PATH_SEPARATOR)
                    .omitEmptyStrings()
                    .splitToList(fullPath)
                    .stream()
                    .map(nameFilter)
                    .collect(Collectors.toList())
             )
            .build();
        nodes.forEach(ZPathImpl::validate);
        return new ZPathImpl(nodes, null, null);
    }

    public static ZPath from(String[] names)
    {
        return from(null, Arrays.asList(names));
    }

    public static ZPath from(List<String> names)
    {
        return from(null, names);
    }

    public static ZPath from(ZPath base, String[] names)
    {
        return from(base, Arrays.asList(names));
    }

    public static ZPath from(ZPath base, List<String> names)
    {
        names = Objects.requireNonNull(names, "names cannot be null");
        names.forEach(ZPathImpl::validate);
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        if ( base != null )
        {
            if ( base instanceof ZPathImpl )
            {
                builder.addAll(((ZPathImpl)base).nodes);
            }
            else
            {
                builder.addAll(Splitter.on(ZKPaths.PATH_SEPARATOR).omitEmptyStrings().splitToList(base.fullPath()));
            }
        }
        else
        {
            builder.add(ZKPaths.PATH_SEPARATOR);
        }
        List<String> nodes = builder.addAll(names).build();
        return new ZPathImpl(nodes, null, null);
    }

    @Override
    public ZPath at(String child)
    {
        return new ZPathImpl(nodes, child, parameterSuppliers);
    }

    @Override
    public ZPath parent()
    {
        checkRootAccess();
        return new ZPathImpl(nodes.subList(0, nodes.size() - 1), null, parameterSuppliers);
    }

    @Override
    public boolean isRoot()
    {
        return nodes.size() == 1;
    }

    @Override
    public boolean startsWith(ZPath path)
    {
        if ( path instanceof ZPathImpl )
        {
            ZPathImpl rhs = (ZPathImpl)path;
            return (nodes.size() >= rhs.nodes.size()) && nodes.subList(0, rhs.nodes.size()).equals(rhs);
        }
        return false;
    }

    @Override
    public Pattern toSchemaPathPattern()
    {
        return Pattern.compile(fullPath() + ZKPaths.PATH_SEPARATOR + ".*");
    }

    @Override
    public String fullPath()
    {
        checkResolved();
        return buildFullPath(false);
    }

    @Override
    public String parentPath()
    {
        checkRootAccess();
        checkResolved();
        return buildFullPath(true);
    }

    @Override
    public String nodeName()
    {
        return nodes.get(nodes.size() - 1);
    }

    @Override
    public boolean equals(Object o)
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        ZPathImpl zPaths = (ZPathImpl)o;

        return nodes.equals(zPaths.nodes);
    }

    @Override
    public int hashCode()
    {
        return nodes.hashCode();
    }

    @Override
    public String toString()
    {
        String value = nodes.stream().map(name -> name.equals(parameter) ? "{p}" : name).collect(Collectors.joining(ZKPaths.PATH_SEPARATOR, ZKPaths.PATH_SEPARATOR, ""));
        return "ZPathImpl{" + value + '}';
    }

    @Override
    public ZPath resolved(List<Object> parameters)
    {
        Iterator<Object> iterator = parameters.iterator();
        List<String> nodeNames = nodes.stream()
            .map(name -> {
                if ( name.equals(parameter) )
                {
                    if ( !iterator.hasNext() )
                    {
                        throw new IllegalStateException(String.format("Parameter missing for [%s]", nodes.toString()));
                    }
                    return iterator.next().toString();
                }
                return name;
            })
            .collect(Collectors.toList());
        return new ZPathImpl(nodeNames, null, parameterSuppliers);
    }

    @Override
    public ZPath resolving(List<Supplier<Object>> parameterSuppliers)
    {
        parameterSuppliers = Objects.requireNonNull(parameterSuppliers, "parameterSuppliers cannot be null");
        return new ZPathImpl(nodes, null, parameterSuppliers);
    }

    private ZPathImpl(List<String> nodes, String child, List<Supplier<Object>> parameterSuppliers)
    {
        this.parameterSuppliers = parameterSuppliers;
        ImmutableList.Builder<String> builder = ImmutableList.<String>builder().addAll(nodes);
        if ( child != null )
        {
            validate(child);
            builder.add(child);
        }
        this.nodes = builder.build();
        isResolved = (parameterSuppliers != null) || !this.nodes.contains(parameter);
    }

    private void checkRootAccess()
    {
        if ( isRoot() )
        {
            throw new NoSuchElementException("The root has no parent");
        }
    }

    private void checkResolved()
    {
        Preconditions.checkState(isResolved, "This ZPath has not been resolved");
    }

    private String buildFullPath(boolean parent)
    {
        boolean addSeparator = false;
        StringBuilder str = new StringBuilder();
        int size = parent ? (nodes.size() - 1) : nodes.size();
        int parameterIndex = 0;
        for ( int i = 0; i < size; ++i )
        {
            if ( i > 1 )
            {
                str.append(ZKPaths.PATH_SEPARATOR);
            }
            String value = nodes.get(i);
            if ( value.equals(parameter) )
            {
                if ( (parameterSuppliers == null) || (parameterSuppliers.size() <= parameterIndex) )
                {
                    throw new IllegalStateException(String.format("Parameter supplier missing at index [%d] for [%s]", parameterIndex, nodes.toString()));
                }
                value = parameterSuppliers.get(parameterIndex++).get().toString();
            }
            str.append(value);
        }
        return str.toString();
    }

    private static void validate(String nodeName)
    {
        if ( parameter.equals(Objects.requireNonNull(nodeName, "nodeName cannot be null")) )
        {
            return;
        }
        if ( nodeName.equals(ZKPaths.PATH_SEPARATOR) )
        {
            return;
        }
        PathUtils.validatePath(ZKPaths.PATH_SEPARATOR + nodeName);
    }
}
