/**
 * Ex3Test - Unit tests for the Ex3 class
 * 
 * This class contains test cases for validating the Ex3 implementation.
 * 
 * @author SALDMAN
 */
public class Ex3Test {
    
    /**
     * Test method for basic functionality
     */
    public static void testBasic() {
        Ex3 ex = new Ex3();
        String info = ex.getInfo();
        
        if (info != null && info.contains("Ex3")) {
            System.out.println("✓ Basic test passed");
        } else {
            System.out.println("✗ Basic test failed");
        }
    }
    
    /**
     * Main method to run all tests
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.out.println("Running Ex3 Tests...");
        System.out.println("====================");
        
        testBasic();
        
        System.out.println("====================");
        System.out.println("Tests completed.");
    }
}
