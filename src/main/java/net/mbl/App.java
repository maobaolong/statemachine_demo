package net.mbl;

import net.mbl.state.VisualizeStateMachine;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws Exception {
        VisualizeStateMachine.main(
                new String[] {"imageName", "net.mbl.statedemo.LocalizedResource", "filename"});
        System.out.println( "Hello World!" );
    }
}
