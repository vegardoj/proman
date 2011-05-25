
package no.vegardoj.games.proman;

/**
 *
 * @author vegardoj
 */
/**
    A Grub is a Creature that moves slowly on the ground.
*/
public class Grub extends Creature {

    public Grub(Animation left, Animation right,
        Animation deadLeft, Animation deadRight)
    {
        super(left, right, deadLeft, deadRight);
    }


    @Override
    public float getMaxSpeed() {
        return 0.05f;
    }

}