package assignments.Ex3;

import exe.ex3.game.Game;
import exe.ex3.game.GhostCL;
import exe.ex3.game.PacManAlgo;
import exe.ex3.game.PacmanGame;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Ex3Algo - Refactored and optimized PacMan Algorithm.
 */
public class Ex3Algo implements PacManAlgo {
    private int tickCounter;
    private static final int SAFE_DISTANCE_VAL = 1000;
    private static final int WALL_VAL = -1;
    // Global safety state set every tick so helpers can consult them without changing many signatures
    private Map2D _lastGhostHeatMap = null;
    private List<Index2D> _dangerousGhostsGlobal = new ArrayList<>();
    // New: simple behavioral mode state
    private static final int MODE_COLLECT = 0;
    private static final int MODE_HUNT = 1;
    // Force PacMan to always avoid ghosts, even when edible
    private static final boolean ALWAYS_AVOID_GHOSTS = true;
    private int _mode = MODE_COLLECT;
    private int _modeLock = 0; // ticks to stay in current mode
    private int _lastPelletCount = -1;
    // Track green (power) pellet eating to enforce cooldown between picks
    private int _lastGreenEatTick = -1; // tickCounter when last green pellet was eaten
    private int _lastGreenCount = -1; // previous tick green count

    public Ex3Algo() {
        tickCounter = 0;
    }

    @Override
    public String getInfo() {
        return "Smart Greedy Algorithm: Prioritizes survival, hunts edible ghosts, and targets closest pellets efficiently.";
    }

    @Override
    public int move(PacmanGame game) {
        // הדפסת מידע ראשונית (רק בהתחלה ולעיתים רחוקות לדיבאג)
        if (tickCounter == 0 || tickCounter == 300) {
            printDebugInfo(game);
        }
        tickCounter++;

        // 1. אתחול נתונים בסיסיים
        int code = 0;
        int[][] boardData = game.getGame(code);
        int blueCode = Game.getIntColor(Color.BLUE, code);

        // המרת מיקום הפקמן
        Pixel2D pacmanPos = toIndex(game.getPos(code));

        GhostCL[] ghosts = game.getGhosts(code);

        // יצירת מפות עזר
        Map2D gameMap = new Map(boardData);
        // pacmanDistances used in some checks; compute once
        Map2D pacmanDistances = gameMap.allDistance(pacmanPos, blueCode);

        // 2. חישוב מפת סכנות רוחות (multi-source BFS -> much faster)
        Map2D ghostHeatMap = computeGhostHeatMap(gameMap, ghosts);

        // update global safety state
        _lastGhostHeatMap = ghostHeatMap;
        _dangerousGhostsGlobal.clear();
        // Treat all ghosts as dangerous to ensure PacMan avoids them regardless of edible state
        for (GhostCL ghost : ghosts) {
            Index2D gp = toIndex(ghost.getPos(0));
            if (gp != null) _dangerousGhostsGlobal.add(gp);
        }

        // 3. החלטה אסטרטגית:

        // א. האם יש רוח אכילה קרובה? (תקיפה)
        // If ALWAYS_AVOID_GHOSTS is set, we never chase edible ghosts
        Pixel2D vulnerableGhost = null;
        if (!ALWAYS_AVOID_GHOSTS) {
            vulnerableGhost = findVulnerableGhost(pacmanDistances, ghosts);
            if (vulnerableGhost != null) {
                // System.out.println("Mode: Hunt Ghost");
                return computeNextStep(gameMap, vulnerableGhost, pacmanPos);
            }
        }

        // ב. האם אנחנו בסכנה מיידית? (בריחה)
        // Trigger Escape state only when a ghost is within a danger radius.
        final int DANGER_RADIUS_TRIGGER = 3; // cells
        int pacGD = ghostHeatMap.getPixel(pacmanPos);
        if (pacGD != SAFE_DISTANCE_VAL && pacGD <= DANGER_RADIUS_TRIGGER) {
            // System.out.println("Mode: Evade");
            return executeEvasion(gameMap, ghostHeatMap, ghosts, pacmanPos);
        }

        // ג. איסוף נקודות (ירוקות ואז ורודות)
        // יצירת מפה זמנית שבה הרוחות הן קירות כדי לא לעבור דרכן בטעות
        Map2D mapWithGhostsAsWalls = treatGhostsAsObstacles(new Map(gameMap.getMap()), ghosts);
        Map2D safeDistances = mapWithGhostsAsWalls.allDistance(pacmanPos, blueCode);

        // collect pellet coordinates once (faster than scanning map multiple times)
        List<Index2D> greenList = new ArrayList<>();
        List<Index2D> pinkList = new ArrayList<>();
        int greenCode = Game.getIntColor(Color.GREEN, code);
        int pinkCode = Game.getIntColor(Color.PINK, code);
        for (int x = 0; x < boardData.length; x++) {
            for (int y = 0; y < boardData[0].length; y++) {
                if (boardData[x][y] == greenCode) greenList.add(new Index2D(x,y));
                else if (boardData[x][y] == pinkCode) pinkList.add(new Index2D(x,y));
            }
        }

        // Detect if a green pellet was eaten since last tick and record the tick
        int currentGreenCount = greenList.size();
        if (_lastGreenCount == -1) {
            _lastGreenCount = currentGreenCount;
        } else {
            if (currentGreenCount < _lastGreenCount) {
                // a green pellet was consumed this tick (by PacMan)
                _lastGreenEatTick = tickCounter;
            }
            _lastGreenCount = currentGreenCount;
        }

        // New improved strategy: score reachable targets (edible ghosts, green power pellets, pink pellets)
        // and pick the best target to chase. This considers path length and path safety (min ghost distance along path).

        double bestScore = Double.NEGATIVE_INFINITY;
        Pixel2D bestTarget = null;
        String bestType = null; // "GHOST", "GREEN", "PINK"

        // helper: evaluate a single candidate target
        java.util.function.BiFunction<Pixel2D, String, Double> evalTarget = (t, type) -> {
            if (t == null) return Double.NEGATIVE_INFINITY;
            Pixel2D[] path = null;
            try { path = gameMap.shortestPath(pacmanPos, t, blueCode); } catch (Exception ignored) {}
            if (path == null) return Double.NEGATIVE_INFINITY;
            int len = path.length - 1; if (len <= 0) return Double.NEGATIVE_INFINITY;
            // require that the path doesn't pass through unsafe cells and compute min ghost distance
            int minGhostDist = Integer.MAX_VALUE;
            for (Pixel2D p : path) {
                if (isUnsafeCell(p)) return Double.NEGATIVE_INFINITY; // path goes too close to a ghost
                int gd = ghostHeatMap.getPixel(p);
                if (gd>=0 && gd<minGhostDist) minGhostDist = gd;
            }
            if (minGhostDist==Integer.MAX_VALUE) minGhostDist = SAFE_DISTANCE_VAL;

            double safety = (minGhostDist==SAFE_DISTANCE_VAL? 1000.0 : (double)minGhostDist);
            double score = 0.0;
            if ("GHOST".equals(type)){
                // check edible time
                double rem = safeRemainTimeForGhostAt(t, ghosts);
                double arriveSec = (len * GameInfo.DT) / 1000.0;
                if (rem <= 0) return Double.NEGATIVE_INFINITY; // not edible anymore
                if (arriveSec > rem) return Double.NEGATIVE_INFINITY; // can't reach in time
                score = 2000.0 + 500.0/(1+len) + 10.0 * safety;
            } else if ("GREEN".equals(type)){
                score = 500.0 + 200.0/(1+len) + 8.0 * safety;
            } else { // PINK
                score = 100.0 + 150.0/(1+len) + 4.0 * safety;
            }
            // small penalty for long routes through low-safety areas
            if (minGhostDist>0 && minGhostDist<3) score -= 300.0;
            return score;
        };

        // New behavior: collect-first-then-hunt
        int totalPellets = greenList.size() + pinkList.size();
        if (_lastPelletCount == -1) _lastPelletCount = totalPellets;

        // Condition to switch to HUNT mode:
        // - few pellets remain OR
        // - there's an edible ghost that's close and reachable in time
        boolean edibleNearby = false;
        for (GhostCL g : ghosts) {
            try {
                Number rr = g.remainTimeAsEatable(0);
                if (rr != null && rr.doubleValue() > 0) {
                    Index2D gp = toIndex(g.getPos(0)); if (gp==null) continue;
                    Map2D dmap = gameMap.allDistance(pacmanPos, blueCode);
                    int dist = (dmap==null? -1 : dmap.getPixel(gp));
                    if (dist > 0) {
                        double arriveSec = (dist * GameInfo.DT) / 1000.0;
                        if (arriveSec + 0.8 < rr.doubleValue()) { edibleNearby = true; break; }
                    }
                }
            } catch (Exception ignored) {}
        }

        // switching rules
        if (!ALWAYS_AVOID_GHOSTS) {
            if (_mode == MODE_COLLECT) {
                if (totalPellets <= 5 || edibleNearby) {
                    _mode = MODE_HUNT; _modeLock = 25; // hunt for a while
                }
            } else { // in HUNT
                if (_modeLock > 0) _modeLock--; else {
                    // return to collect mode if many pellets remain
                    if (totalPellets > 10) _mode = MODE_COLLECT;
                }
            }
        } else {
            // enforce collect mode if we're always avoiding ghosts
            _mode = MODE_COLLECT;
        }

        // Evaluate targets according to mode
        if (_mode == MODE_HUNT) {
            // If not avoiding ghosts, consider hunting; otherwise skip ghosts entirely
            if (!ALWAYS_AVOID_GHOSTS) {
                // consider edible ghosts first
                for (GhostCL g : ghosts){
                    Index2D gp = toIndex(g.getPos(0)); if(gp==null) continue;
                    double s = evalTarget.apply(gp, "GHOST");
                    if(s>bestScore){ bestScore=s; bestTarget=gp; bestType="GHOST"; }
                }
            }
        }

        // Always consider pellets (collect mode prioritizes these)
        // New rule: wait at least MIN_MS_BEFORE_GREEN before pursuing green power pellets to avoid immediate risky pickups
        final int MIN_MS_BEFORE_GREEN = 5000; // 5 seconds
        long elapsedMs = (long)tickCounter * (long)GameInfo.DT;
        boolean allowGreen = (elapsedMs >= MIN_MS_BEFORE_GREEN);
        // enforce cooldown after a green pellet was eaten: wait MIN_MS_BEFORE_GREEN after each eat
        if (_lastGreenEatTick != -1) {
            long sinceEatMs = (long)(tickCounter - _lastGreenEatTick) * (long)GameInfo.DT;
            if (sinceEatMs < MIN_MS_BEFORE_GREEN) allowGreen = false;
        }
        if (allowGreen) {
            for (Index2D g : greenList){ double s = evalTarget.apply(g, "GREEN"); if(s>bestScore){ bestScore=s; bestTarget=g; bestType="GREEN"; } }
        }
        // pink pellets (small pellets) are still collected anytime
        for (Index2D p : pinkList){ double s = evalTarget.apply(p, "PINK"); if(s>bestScore){ bestScore=s; bestTarget=p; bestType="PINK"; } }

        // update pellet history
        _lastPelletCount = totalPellets;

        if(bestTarget!=null){
            return computeNextStep(gameMap, bestTarget, pacmanPos);
        }

        // fallback: safe neighbor or random
        int safe = chooseSafeDirection(gameMap, new Index2D(pacmanPos), blueCode);
        if(safe!=Integer.MIN_VALUE) return safe;
        return getRandomDirection();
    }

    /**
     * פונקציה גנרית למציאת המטרה הקרובה ביותר (וורוד או ירוק)
     */
    private Pixel2D findNearestTarget(Map2D map, Map2D distanceMap, int targetColor) {
        Pixel2D bestTarget = null;
        int minDistance = Integer.MAX_VALUE;

        // fast-path: if both maps are the concrete Map class, access underlying arrays directly
        if (map instanceof Map && distanceMap instanceof Map) {
            int[][] mArr = ((Map) map).getMap();
            int[][] dArr = ((Map) distanceMap).getMap();
            int W = mArr.length;
            int H = mArr[0].length;
            for (int x = 0; x < W; x++) {
                for (int y = 0; y < H; y++) {
                    if (mArr[x][y] == targetColor) {
                        int dist = dArr[x][y];
                        if (dist != -1 && dist < minDistance) {
                            minDistance = dist;
                            bestTarget = new Index2D(x, y);
                        }
                    }
                }
                return bestTarget;
            }
        }
        for (int x = 0; x < map.getWidth(); x++) {
            for (int y = 0; y < map.getHeight(); y++) {
                if (map.getPixel(x, y) == targetColor) {
                    int dist = distanceMap.getPixel(x, y);
                    if (dist != -1 && dist < minDistance) {
                        minDistance = dist;
                        bestTarget = new Index2D(x, y);
                    }
                }
            }
        }
        return bestTarget;
    }

    /**
     * יוצר מפה שבה כל פיקסל מקבל ציון: כמה הוא קרוב לרוח מסוכנת.
     * ערך 1000 מסמן "בטוח". ערך נמוך מסמן סכנה.
     */
    private Map computeGhostHeatMap(Map2D map, GhostCL[] ghosts) {
        int W = map.getWidth();
        int H = map.getHeight();
        int blueCode = Game.getIntColor(Color.BLUE, 0);

        // distance from nearest dangerous ghost, -1 = unreachable / no ghost
        int[][] dist = new int[W][H];
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) dist[x][y] = -1;

        Deque<Index2D> q = new ArrayDeque<>();

        // seed queue with dangerous ghosts (not edible) that are reachable and not in cage
        for (GhostCL ghost : ghosts) {
            Index2D gp = toIndex(ghost.getPos(0));
            if (gp == null) continue;
            if (isInsideCage(gp)) continue;
            double remain = 0;
            Number remNum = null;
            try { remNum = ghost.remainTimeAsEatable(0); } catch (Exception ignored) {}
            if (remNum != null) remain = remNum.doubleValue();
            // treat ghost as dangerous when there is no edible time remaining
            if (remain <= 0) {
                // only seed if the ghost cell is not a wall
                try {
                    if (map.getPixel(gp) != blueCode) {
                        dist[gp.getX()][gp.getY()] = 0;
                        q.addLast(new Index2D(gp));
                    }
                } catch (Exception ignored) {}
            }
        }

        int[] dx = {-1,1,0,0};
        int[] dy = {0,0,-1,1};
        boolean cyclic = map.isCyclic();

        while (!q.isEmpty()) {
            Index2D cur = q.removeFirst();
            int cx = cur.getX();
            int cy = cur.getY();
            for (int k = 0; k < 4; k++) {
                int nx = cx + dx[k];
                int ny = cy + dy[k];
                if (cyclic) {
                    nx = ((nx % W) + W) % W;
                    ny = ((ny % H) + H) % H;
                } else {
                    if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
                }
                if (dist[nx][ny] != -1) continue;
                try {
                    if (map.getPixel(nx, ny) == blueCode) continue; // wall
                } catch (Exception ignored) { continue; }
                dist[nx][ny] = dist[cx][cy] + 1;
                q.addLast(new Index2D(nx, ny));
            }
        }

        // build heat map: SAFE_DISTANCE_VAL for safe, WALL_VAL for walls, else distance
        Map heatMap = new Map(W, H, SAFE_DISTANCE_VAL);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                try {
                    if (map.getPixel(x, y) == blueCode) {
                        heatMap.setPixel(x, y, WALL_VAL);
                    } else if (dist[x][y] != -1) {
                        heatMap.setPixel(x, y, dist[x][y]);
                    }
                } catch (Exception ignored) {}
            }
        }

        return heatMap;
    }

    /**
     * מחשב את הצעד הבא כדי להגיע ליעד
     */
    private int computeNextStep(Map2D map, Pixel2D target, Pixel2D currentPos) {
        int blueCode = Game.getIntColor(Color.BLUE, 0);
        // compute distances from target (one BFS) and move to neighbor with distance-1
        Map2D targetDist = map.allDistance(target, blueCode);
        if (targetDist == null) return getRandomDirection();

        int currDist = targetDist.getPixel(currentPos);
        if (currDist <= 0) {
            // either we're at target or unreachable
            return getRandomDirection();
        }

        // examine neighbors and pick one with dist == currDist - 1
        Pixel2D[] nbrs = getNeighbors(currentPos, map.getWidth(), map.getHeight(), map.isCyclic());
        for (Pixel2D n : nbrs) {
            if (!map.isInside(n)) continue;
            // never step into an unsafe cell
            if (isUnsafeCell(n)) continue;
            int d = targetDist.getPixel(n);
            if (d == currDist - 1) {
                // decide direction from currentPos to n
                int nx = n.getX(), ny = n.getY();
                int cx = currentPos.getX(), cy = currentPos.getY();
                int w = map.getWidth(), h = map.getHeight();
                if (map.isCyclic()) {
                    // normalize deltas
                    int dx = nx - cx; if (dx > 1) dx -= w; if (dx < -1) dx += w;
                    int dy = ny - cy; if (dy > 1) dy -= h; if (dy < -1) dy += h;
                    if (dx == 1) return Game.RIGHT;
                    if (dx == -1) return Game.LEFT;
                    if (dy == 1) return Game.UP; // coordinate system: assume Y+1 is UP per original code
                    if (dy == -1) return Game.DOWN;
                } else {
                    if (nx == cx + 1) return Game.RIGHT;
                    if (nx == cx - 1) return Game.LEFT;
                    if (ny == cy + 1) return Game.UP;
                    if (ny == cy - 1) return Game.DOWN;
                }
            }
        }

        // fallback to shortestPath if neighbor strategy failed
        try {
            Pixel2D[] path = map.shortestPath(currentPos, target, blueCode);
            if (path != null && path.length >= 2) {
                Pixel2D nextNode = path[1];
                // ensure nextNode is safe
                if (!isUnsafeCell(nextNode)) {
                    int cx = currentPos.getX(), cy = currentPos.getY();
                    int nx = nextNode.getX(), ny = nextNode.getY();
                    if (nx > cx) return Game.RIGHT;
                    if (nx < cx) return Game.LEFT;
                    if (ny > cy) return Game.UP;
                    if (ny < cy) return Game.DOWN;
                }
             }
         } catch (Exception ignored) {}

         return getRandomDirection();
    }

    /**
     * לוגיקת בריחה מרוחות
     */
    private int executeEvasion(Map2D map, Map2D ghostHeatMap, GhostCL[] ghosts, Pixel2D currentPos) {
        int blueCode = Game.getIntColor(Color.BLUE, 0);

        // treat non-edible ghosts as obstacles for path planning
        Map2D mapWithObstacles = treatGhostsAsObstacles(new Map(map.getMap()), ghosts);

        // neighbors to consider
        Pixel2D[] neighbors = getNeighbors(currentPos, map.getWidth(), map.getHeight(), map.isCyclic());

        Pixel2D best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        // scoring weights (tweakable)
        final double W_GHOST_DIST = 10.0;   // prefer cells far from ghosts
        final double W_OPEN_AREA = 1.5;     // prefer open space (many reachable cells)
        final double W_OPEN_PATHS = 8.0;    // prefer intersections (more exits)
        final double DEAD_END_PENALTY = 400.0;

        // consider each immediate neighbor and score it
        for (Pixel2D n : neighbors) {
            if (!map.isInside(n)) continue;
            // never consider unsafe neighbors
            if (isUnsafeCell(n)) continue;
            // skip walls and cage tiles
            try { if (map.getPixel(n) == blueCode) continue; } catch (Exception ignored) { continue; }
            if (isInsideCage(n)) continue;

            int ghostDist = ghostHeatMap.getPixel(n);
            if (ghostDist == WALL_VAL) continue; // treat as wall
            if (ghostDist <= 0) continue; // ghost sitting here

            // number of open exits
            int exits = countOpenPassages(map, n, map.isCyclic());

            // avoid dead ends unless forced
            double deadPenalty = (exits <= 1) ? DEAD_END_PENALTY : 0.0;

            // estimate local open area (bounded BFS)
            int openArea = computeOpenArea(map, n, 30);

            // prefer bigger open area and larger ghost-dist
            double score = W_GHOST_DIST * (double)ghostDist + W_OPEN_AREA * (double)openArea + W_OPEN_PATHS * (double)exits - deadPenalty;

            // small bonus for moving away from current pacman ghost distance
            int currGD = ghostHeatMap.getPixel(currentPos);
            if (currGD >= 0 && ghostDist > currGD) score += 5.0;

            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }

        if (best == null) {
            // no good neighbor found: fallback to random safe direction
            int safeDir = chooseSafeDirection(mapWithObstacles, new Index2D(currentPos), blueCode);
            if (safeDir != Integer.MIN_VALUE) return safeDir;
            return getRandomDirection();
        }

        // move towards chosen escape route
        return computeNextStep(mapWithObstacles, best, currentPos);
    }

    // bounded BFS to estimate how much open space is reachable from `start` within `maxCells`.
    private int computeOpenArea(Map2D map, Pixel2D start, int maxCells) {
        int W = map.getWidth(), H = map.getHeight();
        int blueCode = Game.getIntColor(Color.BLUE, 0);
        boolean[][] seen = new boolean[W][H];
        Deque<Pixel2D> q = new ArrayDeque<>();
        int count = 0;
        try {
            q.addLast(start);
            seen[start.getX()][start.getY()] = true;
        } catch (Exception ignored) { return 0; }

        int[] dx = {1,-1,0,0};
        int[] dy = {0,0,1,-1};
        while (!q.isEmpty() && count < maxCells) {
            Pixel2D p = q.removeFirst();
            count++;
            int px = p.getX(), py = p.getY();
            for (int k=0;k<4;k++){
                int nx = px + dx[k], ny = py + dy[k];
                if (nx<0||nx>=W||ny<0||ny>=H) continue;
                if (seen[nx][ny]) continue;
                try { if (map.getPixel(nx,ny) == blueCode) continue; } catch (Exception ignored) { continue; }
                seen[nx][ny] = true;
                q.addLast(new Index2D(nx,ny));
            }
        }
        return count;
    }

    /**
     * מציאת רוח אכילה שמשתלם לרדוף אחריה
     */
    private Pixel2D findVulnerableGhost(Map2D pacmanDistMap, GhostCL[] ghosts) {
        Pixel2D bestTarget = null;
        int minDistance = 15; // לא נרדוף אחרי רוחות רחוקות מדי

        for (GhostCL ghost : ghosts) {
            // בודקים אם הרוח אכילה ויש לה מספיק זמן
            if (ghost.getStatus() != 0 && ghost.remainTimeAsEatable(0) > 0) {
                Index2D ghostPos = toIndex(ghost.getPos(0));
                if (ghostPos == null) continue;
                int dist = -1;
                // fast-path: if pacmanDistMap is a concrete Map, use array
                if (pacmanDistMap instanceof Map) {
                    int[][] darr = ((Map) pacmanDistMap).getMap();
                    int gx = ghostPos.getX(), gy = ghostPos.getY();
                    if (gx >= 0 && gx < darr.length && gy >= 0 && gy < darr[0].length) dist = darr[gx][gy];
                } else {
                    dist = pacmanDistMap.getPixel(ghostPos);
                }

                if (!isInsideCage(ghostPos) && dist != -1) {
                    // חישוב: האם נספיק להגיע לפני שהיא חוזרת להיות מסוכנת?
                    // הוספתי +2 ליתר ביטחון
                    double estimatedTime = ((dist + 2) * GameInfo.DT) / 1000.0;

                    if (dist < minDistance && estimatedTime < ghost.remainTimeAsEatable(0)) {
                        minDistance = dist;
                        bestTarget = ghostPos;
                    }
                }
            }
        }
        return bestTarget;
    }

    // --- Helper Methods ---

    private Pixel2D[] getNeighbors(Pixel2D p, int w, int h, boolean isCyclic) {
        int x = p.getX();
        int y = p.getY();
        if (isCyclic) {
            return new Pixel2D[] {
                    new Index2D((x + 1) % w, y),
                    new Index2D((x - 1 + w) % w, y),
                    new Index2D(x, (y + 1) % h),
                    new Index2D(x, (y - 1 + h) % h)
            };
        } else {
            return new Pixel2D[] {
                    new Index2D(x + 1, y),
                    new Index2D(x - 1, y),
                    new Index2D(x, y + 1),
                    new Index2D(x, y - 1)
            };
        }
    }

    // Robust converter that accepts Pixel2D or a position String
    private Index2D toIndex(Object o) {
        if (o == null) return null;
        if (o instanceof Pixel2D) return new Index2D((Pixel2D)o);
        try {
            String s = o.toString();
            String[] parts = s.split(",");
            if (parts.length >= 2) return new Index2D(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (Exception ignored) {}
        return null;
    }

    private int countOpenPassages(Map2D map, Pixel2D p, boolean isCyclic) {
        int count = 0;
        int blueCode = Game.getIntColor(Color.BLUE, 0);
        Pixel2D[] neighbors = getNeighbors(p, map.getWidth(), map.getHeight(), isCyclic);

        for (Pixel2D n : neighbors) {
            if (map.isInside(n) && map.getPixel(n) != blueCode) {
                count++;
            }
        }
        return count;
    }

    private boolean areGhostsActive(GhostCL[] ghosts) {
        for (GhostCL g : ghosts) {
            if (g.getStatus() != 0) return true;
        }
        return false;
    }

    private boolean isInsideCage(Pixel2D p) {
        // קואורדינטות הכלוב (מותאם למפה הסטנדרטית בתרגיל)
        int x = p.getX();
        int y = p.getY();
        return (x >= 9 && x <= 13 && y >= 11 && y <= 12);
    }

    private Map2D treatGhostsAsObstacles(Map2D map, GhostCL[] ghosts) {
        int blueCode = Game.getIntColor(Color.BLUE, 0);
        for (GhostCL ghost : ghosts) {
            if (ghost.getStatus() == 0) continue; // ignore edible ghosts
            Index2D pos = toIndex(ghost.getPos(0));
            if (pos == null) continue;
            map.setPixel(pos, blueCode); // set ghost position as wall
        }
        return map;
    }

    // --- Debugging ---

    private void printDebugInfo(PacmanGame game) {
        int code = 0;
        int[][] boardData = game.getGame(code);

        System.out.println("Tick: " + tickCounter);
        System.out.println("Pacman Position: " + game.getPos(code));
        System.out.print("Ghosts: ");
        for (GhostCL ghost : game.getGhosts(code)) {
            System.out.print(ghost.getPos(0) + " ");
        }
        System.out.println();

        // Print danger heatmap (ghost distances)
        Map2D heatMap = computeGhostHeatMap(new Map(boardData), game.getGhosts(code));
        for (int y = 0; y < heatMap.getHeight(); y++) {
            for (int x = 0; x < heatMap.getWidth(); x++) {
                int val = heatMap.getPixel(x, y);
                if (val == SAFE_DISTANCE_VAL) System.out.print("."); // safe
                else if (val == WALL_VAL) System.out.print("#"); // wall
                else System.out.print(val % 10); // danger level (last digit)
            }
            System.out.println();
        }
    }

    // Random direction helper (returns one of PacmanGame directions)
    private int getRandomDirection() {
        int[] dirs = {PacmanGame.UP, PacmanGame.LEFT, PacmanGame.DOWN, PacmanGame.RIGHT};
        int idx = (int)(Math.random() * dirs.length);
        return dirs[idx];
    }

    /**
     * מציאת מטרה קרובה מרשימה נתונה (ירוקות או ורודות)
     */
    private Pixel2D findNearestFromList(Map2D distanceMap, List<Index2D> targetList) {
        Pixel2D bestTarget = null;
        int minDistance = Integer.MAX_VALUE;

        for (Index2D target : targetList) {
            int dist = distanceMap.getPixel(target);
            if (dist != -1 && dist < minDistance) {
                minDistance = dist;
                bestTarget = target;
            }
        }
        return bestTarget;
    }

    /**
     * מחשב את הזמן הבטוח להגעה למטרה לפני שהיא מפסיקה להיות אכילה
     */
    private double safeRemainTimeForGhostAt(Pixel2D ghostPos, GhostCL[] ghosts) {
        for (GhostCL g : ghosts) {
            if (g.getStatus() == 0) continue; // ignore edible ghosts
            Index2D gp = toIndex(g.getPos(0));
            if (gp == null) continue;
            if (gp.equals(ghostPos)) {
                try {
                    Number rem = g.remainTimeAsEatable(0);
                    if (rem != null) return rem.doubleValue();
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    // small fallback: choose any safe immediate direction
    private int chooseSafeDirection(Map2D m, Index2D pacPos, int black){
        int[] dirs = {PacmanGame.UP, PacmanGame.LEFT, PacmanGame.DOWN, PacmanGame.RIGHT};
        int[] dx = {0,-1,0,1};
        int[] dy = {-1,0,1,0};
        int W = m.getWidth(); int H = m.getHeight();
        for(int i=0;i<4;i++){
            int nx = pacPos.getX()+dx[i];
            int ny = pacPos.getY()+dy[i];
            if(m.isCyclic()){
                nx = ((nx%W)+W)%W; ny = ((ny%H)+H)%H;
            } else {
                if(nx<0||nx>=W||ny<0||ny>=H) continue;
            }
            Pixel2D cand = new Index2D(nx,ny);
            try{
                int v = m.getPixel(nx,ny);
                if(v!=black && !isUnsafeCell(cand)) return dirs[i];
            }catch(Exception ignored){}
        }
        return Integer.MIN_VALUE;
    }

    /**
     * פונקציה לבדוק אם תא מסוים אינו בטוח (קרוב מדי לרוח מסוכנת)
     */
    private boolean isUnsafeCell(Pixel2D cell) {
        if (_lastGhostHeatMap == null) return false;
        int ghostDist = _lastGhostHeatMap.getPixel(cell);
        return (ghostDist != -1 && ghostDist <= 2); // unsafe if ghost is too close
    }
}
