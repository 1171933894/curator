package org.apache.curator.x.async.modeled.typed;

import org.apache.curator.x.async.modeled.ModelSpec;
import org.apache.curator.x.async.modeled.ModelSpecBuilder;

public interface TypedModelSpec6<M, P1, P2, P3, P4, P5, P6>
{
    ModelSpec<M> resolved(P1 p1, P2 p2, P3 p3, P4 p4, P5 p5, P6 p6);

    /**
     * Return a new TypedModelSpec using the given model spec builder and typed path. When
     * {@link #resolved(Object, Object, Object, Object, Object, Object)} is called the actual model spec is generated with the
     * resolved path
     *
     * @param builder model spec builder
     * @param path typed path
     * @return new TypedModelSpec
     */
    static <M, P1, P2, P3, P4, P5, P6> TypedModelSpec6<M, P1, P2, P3, P4, P5, P6> from(ModelSpecBuilder<M> builder, TypedZPath6<P1, P2, P3, P4, P5, P6> path)
    {
        return (p1, p2, p3, p4, p5, p6) -> builder.withPath(path.resolved(p1, p2, p3, p4, p5, p6)).build();
    }
}
