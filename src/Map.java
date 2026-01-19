package assignments.Ex3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * This class represents a 2D map as a "screen" or a raster matrix or maze over integers.
 * @author boaz.benmoshe
 *
 */
public class Map implements Map2D {
	private int[][] _map;
	private boolean _cyclicFlag = true;

	/**
	 * Constructs a w*h 2D raster map with an init value v.
	 * @param w
	 * @param h
	 * @param v
	 */
	public Map(int w, int h, int v) {init(w,h, v);}
	/**
	 * Constructs a square map (size*size).
	 * @param size
	 */
	public Map(int size) {this(size,size, 0);}

	/**
	 * Constructs a map from a given 2D array.
	 * @param data
	 */
	public Map(int[][] data) {
		init(data);
	}
	@Override
	public void init(int w, int h, int v) {
		/////// add your code below ///////
		if(w<=0 || h<=0) {
			throw new RuntimeException("Width and height must be positive");
		}
		_map = new int[w][h];
		for(int x=0;x<w;x++){
			for(int y=0;y<h;y++){
				_map[x][y]=v;
			}
		}

		///////////////////////////////////
	}
	@Override
	public void init(int[][] arr) {
		/////// add your code below ///////
		if(arr==null) {
			throw new RuntimeException("Input array is null");
		}
		if(arr.length==0) {
			throw new RuntimeException("Input array is empty");
		}
		int w = arr.length;
		int h = arr[0]==null?0:arr[0].length;
		if(h==0) {
			throw new RuntimeException("Input array has empty rows");
		}
		// check ragged
		for(int i=0;i<w;i++){
			if(arr[i]==null || arr[i].length!=h) {
				throw new RuntimeException("Input array is ragged or contains null rows");
			}
		}
		_map = new int[w][h];
		for(int x=0;x<w;x++){
			for(int y=0;y<h;y++){
				_map[x][y]=arr[x][y];
			}
		}

		///////////////////////////////////
	}
	@Override
	public int[][] getMap() {
		int[][] ans = null;
		/////// add your code below ///////
		if(_map==null) return null;
		int w = getWidth();
		int h = getHeight();
		ans = new int[w][h];
		for(int x=0;x<w;x++){
			for(int y=0;y<h;y++){
				ans[x][y]=_map[x][y];
			}
		}

		///////////////////////////////////
		return ans;
	}
	@Override
	/////// add your code below ///////
	public int getWidth() {return _map==null?0:_map.length;}
	@Override
	/////// add your code below ///////
	public int getHeight() {return (_map==null||_map.length==0)?0:_map[0].length;}
	@Override
	/////// add your code below ///////
	public int getPixel(int x, int y) { return _getPixelInternal(x,y);}
	@Override
	/////// add your code below ///////
	public int getPixel(Pixel2D p) {
		return this.getPixel(p.getX(),p.getY());
	}
	@Override
	/////// add your code below ///////
	public void setPixel(int x, int y, int v) { _setPixelInternal(x,y,v);}
	@Override
	/////// add your code below ///////
	public void setPixel(Pixel2D p, int v) {
		_setPixelInternal(p.getX(),p.getY(),v);
	}
	@Override
	/**
	 * Fills this map with the new color (new_v) starting from p.
	 * https://en.wikipedia.org/wiki/Flood_fill
	 */
	public int fill(Pixel2D xy, int new_v) {
		int ans=0;
		/////// add your code below ///////
		if(xy==null) return 0;
		if(_map==null) return 0;
		int w = getWidth();
		int h = getHeight();
		int sx = xy.getX();
		int sy = xy.getY();
		// get original color (may throw if outside and non-cyclic)
		int orig;
		try{
			orig = _getPixelInternal(sx,sy);
		}catch(RuntimeException e){
			return 0;
		}
		if(orig==new_v) return 0;
		boolean[][] visited = new boolean[w][h];
		Deque<Index2D> q = new ArrayDeque<>();
		// normalize start coords to internal indices (wrap if cyclic)
		int startX = sx, startY = sy;
		if(_cyclicFlag){
			startX = ((sx % w) + w) % w;
			startY = ((sy % h) + h) % h;
		} else {
			if(startX<0 || startX>=w || startY<0 || startY>=h) return 0;
		}
		q.add(new Index2D(startX,startY));
		visited[startX][startY]=true;
		_setPixelInternal(startX,startY,new_v);
		ans++;
		int[] dx = {-1,1,0,0};
		int[] dy = {0,0,-1,1};
		while(!q.isEmpty()){
			Index2D cur = q.removeFirst();
			int cx = cur.getX();
			int cy = cur.getY();
			for(int k=0;k<4;k++){
				int nx = cx + dx[k];
				int ny = cy + dy[k];
				if(_cyclicFlag){
					nx = ((nx % w) + w) % w;
					ny = ((ny % h) + h) % h;
				} else {
					if(nx<0 || nx>=w || ny<0 || ny>=h) continue;
				}
				if(visited[nx][ny]) continue;
				if(_map[nx][ny]==orig){
					visited[nx][ny]=true;
					_setPixelInternal(nx,ny,new_v);
					ans++;
					q.add(new Index2D(nx,ny));
				}
			}
		}

		///////////////////////////////////
		return ans;
	}

	@Override
	/**
	 * BFS like shortest the computation based on iterative raster implementation of BFS, see:
	 * https://en.wikipedia.org/wiki/Breadth-first_search
	 */
	public Pixel2D[] shortestPath(Pixel2D p1, Pixel2D p2, int obsColor) {
		Pixel2D[] ans = null;  // the result.
		/////// add your code below ///////
		if(p1==null || p2==null) return null;
		if(_map==null) return null;
		int w = getWidth();
		int h = getHeight();
		int sx = p1.getX();
		int sy = p1.getY();
		int tx = p2.getX();
		int ty = p2.getY();
		// Normalize start and target
		if(!_cyclicFlag){
			if(sx<0||sx>=w||sy<0||sy>=h) return null;
			if(tx<0||tx>=w||ty<0||ty>=h) return null;
		}
		int startX = sx, startY = sy, targetX = tx, targetY = ty;
		if(_cyclicFlag){
			startX = ((sx % w) + w) % w;
			startY = ((sy % h) + h) % h;
			targetX = ((tx % w) + w) % w;
			targetY = ((ty % h) + h) % h;
		}
		// if start or target are obstacles -> no path
		if(_map[startX][startY]==obsColor || _map[targetX][targetY]==obsColor) return null;
		boolean[][] visited = new boolean[w][h];
		Index2D[][] parent = new Index2D[w][h];
		Deque<Index2D> q = new ArrayDeque<>();
		q.add(new Index2D(startX,startY));
		visited[startX][startY]=true;
		boolean found = false;
		int[] dx = {-1,1,0,0};
		int[] dy = {0,0,-1,1};
		while(!q.isEmpty()){
			Index2D cur = q.removeFirst();
			int cx = cur.getX();
			int cy = cur.getY();
			if(cx==targetX && cy==targetY){ found=true; break; }
			for(int k=0;k<4;k++){
				int nx = cx + dx[k];
				int ny = cy + dy[k];
				if(_cyclicFlag){
					nx = ((nx % w) + w) % w;
					ny = ((ny % h) + h) % h;
				} else {
					if(nx<0 || nx>=w || ny<0 || ny>=h) continue;
				}
				if(visited[nx][ny]) continue;
				if(_map[nx][ny]==obsColor) continue;
				visited[nx][ny]=true;
				parent[nx][ny]=cur;
				q.add(new Index2D(nx,ny));
			}
		}
		if(!found) return null;
		// reconstruct path
		List<Pixel2D> path = new ArrayList<>();
		Index2D cur = new Index2D(targetX,targetY);
		while(!(cur.getX()==startX && cur.getY()==startY)){
			path.add(cur);
			cur = parent[cur.getX()][cur.getY()];
		}
		path.add(new Index2D(startX,startY));
		// reverse
		int n = path.size();
		ans = new Pixel2D[n];
		for(int i=0;i<n;i++){
			ans[i]=path.get(n-1-i);
		}

		///////////////////////////////////
		return ans;
	}
	@Override
	/////// add your code below ///////
	public boolean isInside(Pixel2D p) {
		if(p==null || _map==null) return false;
		int x = p.getX();
		int y = p.getY();
		return x>=0 && x<getWidth() && y>=0 && y<getHeight();
	}

	@Override
	/////// add your code below ///////
	public boolean isCyclic() {
		return _cyclicFlag;
	}
	@Override
	/////// add your code below ///////
	public void setCyclic(boolean cy) { _cyclicFlag = cy; }
	@Override
	/////// add your code below ///////
	public Map2D allDistance(Pixel2D start, int obsColor) {
		Map2D ans = null;  // the result.
		/////// add your code below ///////
		if(start==null || _map==null) return null;
		int w = getWidth();
		int h = getHeight();
		int sx = start.getX();
		int sy = start.getY();
		if(!_cyclicFlag){
			if(sx<0||sx>=w||sy<0||sy>=h) return null;
		}
		int startX = sx, startY = sy;
		if(_cyclicFlag){
			startX = ((sx % w) + w) % w;
			startY = ((sy % h) + h) % h;
		}
		// prepare distance map filled with -1
		int[][] dist = new int[w][h];
		for(int x=0;x<w;x++) for(int y=0;y<h;y++) dist[x][y] = -1;
		if(_map[startX][startY]==obsColor){
			// start is an obstacle -> return map with all -1
			return new Map(dist);
		}
		Deque<Index2D> q = new ArrayDeque<>();
		q.add(new Index2D(startX,startY));
		dist[startX][startY]=0;
		int[] dx = {-1,1,0,0};
		int[] dy = {0,0,-1,1};
		while(!q.isEmpty()){
			Index2D cur = q.removeFirst();
			int cx = cur.getX();
			int cy = cur.getY();
			for(int k=0;k<4;k++){
				int nx = cx + dx[k];
				int ny = cy + dy[k];
				if(_cyclicFlag){
					nx = ((nx % w) + w) % w;
					ny = ((ny % h) + h) % h;
				} else {
					if(nx<0 || nx>=w || ny<0 || ny>=h) continue;
				}
				if(dist[nx][ny]!=-1) continue; // visited
				if(_map[nx][ny]==obsColor) continue;
				dist[nx][ny]=dist[cx][cy]+1;
				q.add(new Index2D(nx,ny));
			}
		}
		ans = new Map(dist);

		///////////////////////////////////
		return ans;
	}

	// internal helpers
	private int _getPixelInternal(int x, int y){
		if(_map==null) throw new RuntimeException("Map not initialized");
		int w = getWidth();
		int h = getHeight();
		if(_cyclicFlag){
			int rx = ((x % w) + w) % w;
			int ry = ((y % h) + h) % h;
			return _map[rx][ry];
		} else {
			if(x<0 || x>=w || y<0 || y>=h) throw new RuntimeException("Coordinate out of bounds");
			return _map[x][y];
		}
	}

	private void _setPixelInternal(int x, int y, int v){
		if(_map==null) throw new RuntimeException("Map not initialized");
		int w = getWidth();
		int h = getHeight();
		if(_cyclicFlag){
			int rx = ((x % w) + w) % w;
			int ry = ((y % h) + h) % h;
			_map[rx][ry]=v;
		} else {
			if(x<0 || x>=w || y<0 || y>=h) throw new RuntimeException("Coordinate out of bounds");
			_map[x][y]=v;
		}
	}
}
