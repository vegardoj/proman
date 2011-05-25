
package no.vegardoj.games.proman;

/**
 *
 * @author vegardoj
 */
/**
    A Fly is a Creature that flies slowly in the air.
*/
public class Fly extends Creature {

    public Fly(Animation left, Animation right,
        Animation deadLeft, Animation deadRight)
    {
        super(left, right, deadLeft, deadRight);
    }


    @Override
    public float getMaxSpeed() {
        return 0.2f;
    }


    @Override
    public boolean isFlying() {
        return isAlive();
    }

}
