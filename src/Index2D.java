package assignments.Ex3;

public class Index2D implements Pixel2D {
    private int x;
    private int y;

    /**
     * Constructs a new Index2D with the provided coordinates.
     *
     * @param w the x-coordinate (column)
     * @param h the y-coordinate (row)
     */
    public Index2D(int w, int h) {
        x=w;
        y=h;
    }

    /**
     * Constructs a new Index2D by copying coordinates from another Pixel2D.
     *
     * Note: this constructor dereferences the provided Pixel2D, so if {@code other}
     * is null the call will produce a NullPointerException at runtime.
     *
     * @param other the Pixel2D to copy from (must be non-null)
     */
    public Index2D(Pixel2D other)
    {
        x=other.getX();
        y=other.getY();
    }

    /**
     * Returns the x coordinate of this index.
     *
     * @return x coordinate (column)
     */
    @Override
    public int getX() {
        return x;
    }

    /**
     * Returns the y coordinate of this index.
     *
     * @return y coordinate (row)
     */
    @Override
    public int getY() {
        return y;
    }

    /**
     * Computes the Euclidean distance between this index and another Pixel2D.
     *
     * Behavior note: the implementation returns 0 when the provided argument is null.
     * Mathematically this method returns sqrt((x - other.x)^2 + (y - other.y)^2).
     *
     * @param p2 the other Pixel2D to measure distance to (may be null)
     * @return Euclidean distance as a double; 0 if {@code p2} is null
     */
    @Override
    public double distance2D(Pixel2D p2) {
        if(p2==null){
            return 0;
        }
        return Math.sqrt(Math.pow(x-p2.getX(),2)+Math.pow(y-p2.getY(),2));
    }

    /**
     * Returns a string representation of this Index2D.
     *
     * Implementation returns a compact representation: "Index2D{x=...,y=...}".
     *
     * @return string representation of the coordinate
     */
    @Override
    public String toString() {
        return "Index2D{x="+x+",y="+y+"}";
    }

    /**
     * Equality is defined by type and coordinate equality.
     *
     * The method returns true when the provided object is also an Index2D and has the
     * same x and y values. It returns false for null or objects of other types.
     *
     * @param p object to compare to
     * @return true if {@code p} is an Index2D with identical coordinates; false otherwise
     */
    @Override
    public boolean equals(Object p) {
        if(p==null){
            return false;
        }
        return p instanceof Index2D && x==((Index2D) p).getX() && y==((Index2D) p).getY();
    }
}