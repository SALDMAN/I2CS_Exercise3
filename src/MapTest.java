package assignments.Ex3;

public class MapTest {
    public static void main(String[] args) {
        System.out.println("MapTest started");
        // create 5x5 map initialized with 0
        Map m = new Map(5,5,0);
        m.setCyclic(false);
        // add a horizontal wall of obstacles (value 1) in row 2
        for(int x=0;x<5;x++) m.setPixel(x,2,1);
        System.out.println("Initial map:");
        printMap(m);

        // try shortest path from (0,0) to (4,4) - should be null due to wall
        Pixel2D s = new Index2D(0,0);
        Pixel2D t = new Index2D(4,4);
        Pixel2D[] path = m.shortestPath(s,t,1);
        System.out.println("Shortest path (non-cyclic) from (0,0) to (4,4) avoiding 1:");
        if(path==null) System.out.println("No path (as expected)");
        else printPath(path);

        // enable cyclic and test again (wrap around)
        m.setCyclic(true);
        path = m.shortestPath(s,t,1);
        System.out.println("Shortest path (cyclic) from (0,0) to (4,4) avoiding 1:");
        if(path==null) System.out.println("No path (unexpected)");
        else printPath(path);

        // test fill: change region containing (0,0) from 0 -> 2
        int filled = m.fill(new Index2D(0,0), 2);
        System.out.println("Filled cells count: " + filled);
        printMap(m);

        // test allDistance from (0,0) with obs=1
        Map2D distMap = m.allDistance(new Index2D(0,0), 1);
        System.out.println("Distance map from (0,0) with obs=1:");
        if(distMap!=null) printDist(distMap);

        System.out.println("MapTest finished");
    }

    private static void printMap(Map m){
        int[][] a = m.getMap();
        for(int y=0;y<m.getHeight();y++){
            for(int x=0;x<m.getWidth();x++){
                System.out.print(a[x][y] + " ");
            }
            System.out.println();
        }
    }

    private static void printPath(Pixel2D[] path){
        for(int i=0;i<path.length;i++){
            System.out.print("("+path[i].getX()+","+path[i].getY()+") ");
        }
        System.out.println();
    }

    private static void printDist(Map2D m){
        for(int y=0;y<m.getHeight();y++){
            for(int x=0;x<m.getWidth();x++){
                System.out.print(m.getPixel(x,y) + "\t");
            }
            System.out.println();
        }
    }
}

