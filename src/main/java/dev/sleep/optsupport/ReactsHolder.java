package dev.sleep.optsupport;

/** Added to BlockState by NeighborReactStateMixin: cached "reacts to neighbour updates" (-1 = not computed). */
public interface ReactsHolder {
    boolean sleep$reacts();
}
