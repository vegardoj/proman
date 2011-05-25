
package no.vegardoj.games.proman;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Transparency;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.sampled.AudioFormat;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import no.vegardoj.games.proman.sound.EchoFilter;
import no.vegardoj.games.proman.sound.MidiPlayer;
import no.vegardoj.games.proman.sound.Sound;
import no.vegardoj.games.proman.sound.SoundManager;

/**
 *
 * @author vegardoj
 */
/**
    GameManager manages all parts of the game.
*/
public class GameManager extends GameCore implements ActionListener {

    public static void main(String[] args) {
        new GameManager().run();
    }

    // uncompressed, 44100Hz, 16-bit, mono, signed, little-endian
    private static final AudioFormat PLAYBACK_FORMAT =
        new AudioFormat(44100, 16, 1, true, false);

    private static final int DRUM_TRACK = 1;

    public static final float GRAVITY = 0.002f;

    private Point pointCache = new Point();
    private TileMap map;
    private MidiPlayer midiPlayer;
    private SoundManager soundManager;
    private ResourceManager resourceManager;
    private Sound prizeSound;
    private Sound boopSound;
    private InputManager inputManager;
    private TileMapRenderer renderer;

    private GameAction moveLeft;
    private GameAction moveRight;
    private GameAction jump;
    private GameAction exit;
    private GameAction pause;

    protected GameAction configAction;

    private JButton playButton;
    private JButton configButton;
    private JButton quitButton;
    private JButton pauseButton;
    private JPanel playButtonSpace;

    private static final String INSTRUCTIONS =
        "<html>Click an action's input box to change its keys." +
        "<br>An action can have at most three keys associated " +
        "with it.<br>Press Backspace to clear an action's keys.";

    private JPanel dialog;
    private JButton okButton;
    private List inputs;

    private boolean paused;


    @Override
    public void init() {
        super.init();

        // set up input manager
        Window window = screen.getFullScreenWindow();
        inputManager = new InputManager(window);

        initInput();
        paused = false;

        // start resource manager
        resourceManager = new ResourceManager(
        screen.getFullScreenWindow().getGraphicsConfiguration());

        // load resources
        renderer = new TileMapRenderer();
        renderer.setBackground(
            resourceManager.loadImage("background.png"));

        // load first map
        map = resourceManager.loadNextMap();
        System.out.println("FIRST MAP LOADED");
        // load sounds
        soundManager = new SoundManager(PLAYBACK_FORMAT);
        prizeSound = soundManager.getSound("src/main/java/no/vegardoj/games/proman/sounds/prize.wav");
        boopSound = soundManager.getSound("src/main/java/no/vegardoj/games/proman/sounds/boop2.wav");

        // start music
        midiPlayer = new MidiPlayer();
        Sequence sequence =
            midiPlayer.getSequence("src/main/java/no/vegardoj/games/proman/sounds/music.midi");
        midiPlayer.play(sequence, true);
        toggleDrumPlayback();
    }


    /**
        Closes any resurces used by the GameManager.
    */
    @Override
    public void stop() {
        super.stop();
        midiPlayer.close();
        soundManager.close();
    }

    public void checkSystemInput() {
        if (pause.isPressed()) {
            setPaused(!isPaused());
            midiPlayer.setPaused(isPaused());

        }
        if (exit.isPressed()) {
            stop();
        }

        if (configAction.isPressed()) {
            // hide or show the config dialog
            boolean show = !dialog.isVisible();
            dialog.setVisible(show);
            setPaused(show);
        }
    }


    private void initInput() {
        jump = new GameAction("jump",
            GameAction.DETECT_INITAL_PRESS_ONLY);
        exit = new GameAction("exit",
            GameAction.DETECT_INITAL_PRESS_ONLY);
        moveLeft = new GameAction("moveLeft");
        moveRight = new GameAction("moveRight");
        pause = new GameAction("pause",
            GameAction.DETECT_INITAL_PRESS_ONLY);

        inputManager.mapToKey(exit, KeyEvent.VK_ESCAPE);
        inputManager.mapToKey(pause, KeyEvent.VK_P);

        // jump with spacebar or mouse button
        inputManager.mapToKey(jump, KeyEvent.VK_SPACE);
        inputManager.mapToMouse(jump,
            InputManager.MOUSE_BUTTON_1);

        // move with the arrow keys...
        //inputManager.mapToKey(moveLeft, KeyEvent.VK_LEFT);
        //inputManager.mapToKey(moveRight, KeyEvent.VK_RIGHT);

        // ... or with A and D.
        inputManager.mapToKey(moveLeft, KeyEvent.VK_A);
        inputManager.mapToKey(moveRight, KeyEvent.VK_D);

        // make sure Swing components don't paint themselves
        NullRepaintManager.install();

        // create an additional GameAction for "config"
        configAction = new GameAction("config");

        // create buttons
        quitButton = createButton("quit", "Quit");
        playButton = createButton("play", "Continue");
        pauseButton = createButton("pause", "Pause");
        configButton = createButton("config", "Change Settings");

        // create the space where the play/pause buttons go.
        playButtonSpace = new JPanel();
        playButtonSpace.setOpaque(false);
        playButtonSpace.add(pauseButton);

        JFrame frame = (JFrame) super.screen.getFullScreenWindow();
        Container contentPane = frame.getContentPane();

        // make sure the content pane is transparent
        if (contentPane instanceof JComponent) {
            ((JComponent)contentPane).setOpaque(false);
        }

        // add components to the screen's content pane
        contentPane.setLayout(new FlowLayout(FlowLayout.LEFT));
        contentPane.add(playButtonSpace);
        contentPane.add(configButton);
        contentPane.add(quitButton);

        // explicitly lay out components (needed on some systems)
        frame.validate();

        inputs = new ArrayList();

        // create the list of GameActions and mapped keys
        JPanel configPanel = new JPanel(new GridLayout(5,2,2,2));
        addActionConfig(configPanel, moveLeft);
        addActionConfig(configPanel, moveRight);
        addActionConfig(configPanel, jump);
        addActionConfig(configPanel, pause);
        addActionConfig(configPanel, exit);

        // create the panel containing the OK button
        JPanel bottomPanel = new JPanel(new FlowLayout());
        okButton = new JButton("OK");
        okButton.setFocusable(false);
        okButton.addActionListener(this);
        bottomPanel.add(okButton);

        // create the panel containing the instructions.
        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(new JLabel(INSTRUCTIONS));

        // create the dialog border
        Border border =
            BorderFactory.createLineBorder(Color.black);

        // create the config dialog.
        dialog = new JPanel(new BorderLayout());
        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(configPanel, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.setBorder(border);
        dialog.setVisible(false);
        dialog.setSize(dialog.getPreferredSize());

        // center the dialog
        dialog.setLocation(
            (screen.getWidth() - dialog.getWidth()) / 2,
            (screen.getHeight() - dialog.getHeight()) / 2);

        // add the dialog to the "modal dialog" layer of the
        // screen's layered pane.
        screen.getFullScreenWindow().add(dialog,
            JLayeredPane.MODAL_LAYER);
    }

     /**
        Tests whether the game is paused or not.
    */
    public boolean isPaused() {
        return paused;
    }


    /**
        Sets the paused state.
    */
    public void setPaused(boolean p) {
        if (paused != p) {
            this.paused = p;
            inputManager.resetAllGameActions();
        }
        playButtonSpace.removeAll();
        if (isPaused()) {
            playButtonSpace.add(playButton);
        }
        else {
            playButtonSpace.add(pauseButton);
        }
    }


    private void checkInput(long elapsedTime) {

        if (exit.isPressed()) {
            stop();
        }

        Player player = (Player)map.getPlayer();
        if (player.isAlive()) {
            float velocityX = 0;
            if (moveLeft.isPressed()) {
                velocityX-=player.getMaxSpeed();
            }
            if (moveRight.isPressed()) {
                velocityX+=player.getMaxSpeed();
            }
            if (jump.isPressed()) {
                player.jump(false);
            }
            player.setVelocityX(velocityX);
        }

    }


    public void draw(Graphics2D g) {
        renderer.draw(g, map,
            screen.getWidth(), screen.getHeight());

        JFrame frame = (JFrame) super.screen.getFullScreenWindow();

        // the layered pane contains things like popups (tooltips,
        // popup menus) and the content pane.
        frame.getLayeredPane().paintComponents(g);
    }


    /**
        Gets the current map.
    */
    public TileMap getMap() {
        return map;
    }


    /**
        Turns on/off drum playback in the midi music (track 1).
    */
    public void toggleDrumPlayback() {
        Sequencer sequencer = midiPlayer.getSequencer();
        if (sequencer != null) {
            sequencer.setTrackMute(DRUM_TRACK,
                !sequencer.getTrackMute(DRUM_TRACK));
        }
    }


    /**
        Gets the tile that a Sprites collides with. Only the
        Sprite's X or Y should be changed, not both. Returns null
        if no collision is detected.
    */
    public Point getTileCollision(Sprite sprite,
        float newX, float newY)
    {
        float fromX = Math.min(sprite.getX(), newX);
        float fromY = Math.min(sprite.getY(), newY);
        float toX = Math.max(sprite.getX(), newX);
        float toY = Math.max(sprite.getY(), newY);

        // get the tile locations
        int fromTileX = TileMapRenderer.pixelsToTiles(fromX);
        int fromTileY = TileMapRenderer.pixelsToTiles(fromY);
        int toTileX = TileMapRenderer.pixelsToTiles(
            toX + sprite.getWidth() - 1);
        int toTileY = TileMapRenderer.pixelsToTiles(
            toY + sprite.getHeight() - 1);

        // check each tile for a collision
        for (int x=fromTileX; x<=toTileX; x++) {
            for (int y=fromTileY; y<=toTileY; y++) {
                if (x < 0 || x >= map.getWidth() ||
                    map.getTile(x, y) != null)
                {
                    // collision found, return the tile
                    pointCache.setLocation(x, y);
                    return pointCache;
                }
            }
        }

        // no collision found
        return null;
    }


    /**
        Checks if two Sprites collide with one another. Returns
        false if the two Sprites are the same. Returns false if
        one of the Sprites is a Creature that is not alive.
    */
    public boolean isCollision(Sprite s1, Sprite s2) {
        // if the Sprites are the same, return false
        if (s1 == s2) {
            return false;
        }

        // if one of the Sprites is a dead Creature, return false
        if (s1 instanceof Creature && !((Creature)s1).isAlive()) {
            return false;
        }
        if (s2 instanceof Creature && !((Creature)s2).isAlive()) {
            return false;
        }

        // get the pixel location of the Sprites
        int s1x = Math.round(s1.getX());
        int s1y = Math.round(s1.getY());
        int s2x = Math.round(s2.getX());
        int s2y = Math.round(s2.getY());

        // check if the two sprites' boundaries intersect
        return (s1x < s2x + s2.getWidth() &&
            s2x < s1x + s1.getWidth() &&
            s1y < s2y + s2.getHeight() &&
            s2y < s1y + s1.getHeight());
    }


    /**
        Gets the Sprite that collides with the specified Sprite,
        or null if no Sprite collides with the specified Sprite.
    */
    public Sprite getSpriteCollision(Sprite sprite) {

        // run through the list of Sprites
        Iterator i = map.getSprites();
        while (i.hasNext()) {
            Sprite otherSprite = (Sprite)i.next();
            if (isCollision(sprite, otherSprite)) {
                // collision found, return the Sprite
                return otherSprite;
            }
        }

        // no collision found
        return null;
    }


    /**
        Updates Animation, position, and velocity of all Sprites
        in the current map.
    */
    @Override
    public void update(long elapsedTime) {
        Creature player = (Creature)map.getPlayer();


        // player is dead! start map over
        if (player.getState() == Creature.STATE_DEAD) {
            map = resourceManager.reloadMap();
            return;
        }

        // get keyboard/mouse input
        checkInput(elapsedTime);
        checkSystemInput();

        if(!paused) {
            // update player
            updateCreature(player, elapsedTime);
            player.update(elapsedTime);

            // update other sprites
            Iterator i = map.getSprites();
            while (i.hasNext()) {
                Sprite sprite = (Sprite)i.next();
                if (sprite instanceof Creature) {
                    Creature creature = (Creature)sprite;
                    if (creature.getState() == Creature.STATE_DEAD) {
                        i.remove();
                    }
                    else {
                        updateCreature(creature, elapsedTime);
                    }
                }
                // normal update
                sprite.update(elapsedTime);
            }

        }


    }


    /**
        Updates the creature, applying gravity for creatures that
        aren't flying, and checks collisions.
    */
    private void updateCreature(Creature creature,
        long elapsedTime)
    {

        // apply gravity
        if (!creature.isFlying()) {
            creature.setVelocityY(creature.getVelocityY() +
                GRAVITY * elapsedTime);
        }

        // change x
        float dx = creature.getVelocityX();
        float oldX = creature.getX();
        float newX = oldX + dx * elapsedTime;
        Point tile =
            getTileCollision(creature, newX, creature.getY());
        if (tile == null) {
            creature.setX(newX);
        }
        else {
            // line up with the tile boundary
            if (dx > 0) {
                creature.setX(
                    TileMapRenderer.tilesToPixels(tile.x) -
                    creature.getWidth());
            }
            else if (dx < 0) {
                creature.setX(
                    TileMapRenderer.tilesToPixels(tile.x + 1));
            }
            creature.collideHorizontal();
        }
        if (creature instanceof Player) {
            checkPlayerCollision((Player)creature, false);
        }

        // change y
        float dy = creature.getVelocityY();
        float oldY = creature.getY();
        float newY = oldY + dy * elapsedTime;
        tile = getTileCollision(creature, creature.getX(), newY);
        if (tile == null) {
            creature.setY(newY);
        }
        else {
            // line up with the tile boundary
            if (dy > 0) {
                creature.setY(
                    TileMapRenderer.tilesToPixels(tile.y) -
                    creature.getHeight());
            }
            else if (dy < 0) {
                creature.setY(
                    TileMapRenderer.tilesToPixels(tile.y + 1));
            }
            creature.collideVertical();
        }
        if (creature instanceof Player) {
            boolean canKill = (oldY < creature.getY());
            checkPlayerCollision((Player)creature, canKill);
        }

    }


    /**
        Checks for Player collision with other Sprites. If
        canKill is true, collisions with Creatures will kill
        them.
    */
    public void checkPlayerCollision(Player player,
        boolean canKill)
    {
        if (!player.isAlive()) {
            return;
        }

        // check for player collision with other sprites
        Sprite collisionSprite = getSpriteCollision(player);
        if (collisionSprite instanceof PowerUp) {
            acquirePowerUp((PowerUp)collisionSprite);
        }
        else if (collisionSprite instanceof Creature) {
            Creature badguy = (Creature)collisionSprite;
            if (canKill) {
                // kill the badguy and make player bounce
                soundManager.play(boopSound);
                badguy.setState(Creature.STATE_DYING);
                player.setY(badguy.getY() - player.getHeight());
                player.jump(true);
            }
            else {
                // player dies!
                player.setState(Creature.STATE_DYING);
            }
        }
    }


    /**
        Gives the player the speicifed power up and removes it
        from the map.
    */
    public void acquirePowerUp(PowerUp powerUp) {
        // remove it from the map
        map.removeSprite(powerUp);

        if (powerUp instanceof PowerUp.Star) {
            // do something here, like give the player points
            soundManager.play(prizeSound);
        }
        else if (powerUp instanceof PowerUp.Music) {
            // change the music
            soundManager.play(prizeSound);
            toggleDrumPlayback();
        }
        else if (powerUp instanceof PowerUp.Goal) {
            // advance to next map
            soundManager.play(prizeSound,
                new EchoFilter(2000, .7f), false);
            map = resourceManager.loadNextMap();
        }
        else if (powerUp instanceof PowerUp.Lever) {
            soundManager.play(prizeSound);
            //inputManager.mapToKey(moveLeft, KeyEvent.VK_D);
            //inputManager.mapToKey(moveRight, KeyEvent.VK_A);

        }
    }


    public JButton createButton(String name, String toolTip) {
        
        String imagePath = "src/main/java/no/vegardoj/games/proman/images/menu/" + name + ".png";        
        ImageIcon iconRollover = new ImageIcon(imagePath);

        int w = iconRollover.getIconWidth();
        int h = iconRollover.getIconHeight();


        // get the cursor for this button
        Cursor cursor =
            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

        // make translucent default image
        Image image = screen.createCompatibleImage(w, h,
            Transparency.TRANSLUCENT);


        Graphics2D g = (Graphics2D)image.getGraphics();
        Composite alpha = AlphaComposite.getInstance(
            AlphaComposite.SRC_OVER, .5f);
        g.setComposite(alpha);
        g.drawImage(iconRollover.getImage(), 0, 0, null);
        g.dispose();
        ImageIcon iconDefault = new ImageIcon(image);

        // make a pressed image
        image = screen.createCompatibleImage(w, h,
            Transparency.TRANSLUCENT);
        g = (Graphics2D)image.getGraphics();
        g.drawImage(iconRollover.getImage(), 2, 2, null);
        g.dispose();
        ImageIcon iconPressed = new ImageIcon(image);

        // create the button
        JButton button = new JButton();
        button.addActionListener(this);
        button.setIgnoreRepaint(true);
        button.setFocusable(false);
        button.setToolTipText(toolTip);
        button.setBorder(null);
        button.setContentAreaFilled(false);
        button.setCursor(cursor);
        button.setIcon(iconDefault);
        button.setRolloverIcon(iconRollover);
        button.setPressedIcon(iconPressed);

        return button;
    }

    private void addActionConfig(JPanel configPanel,
        GameAction action)
    {
        JLabel label = new JLabel(action.getName(), JLabel.RIGHT);
        InputComponent input = new InputComponent(action);
        configPanel.add(label);
        configPanel.add(input);
        inputs.add(input);
    }

    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == quitButton) {
            // fire the "exit" gameAction
            exit.tap();
        }
        else if (src == configButton) {
            // doesn't do anything (for now)
            configAction.tap();
        }
        else if (src == playButton || src == pauseButton) {
            // fire the "pause" gameAction
            pause.tap();
        }
        else if (e.getSource() == okButton) {
            // hides the config dialog
            configAction.tap();
        }
    }

    class InputComponent extends JTextField  {

        private GameAction action;

        /**
            Creates a new InputComponent for the specified
            GameAction.
        */
        public InputComponent(GameAction action) {
            this.action = action;
            setText();
            enableEvents(KeyEvent.KEY_EVENT_MASK |
                MouseEvent.MOUSE_EVENT_MASK |
                MouseEvent.MOUSE_MOTION_EVENT_MASK |
                MouseEvent.MOUSE_WHEEL_EVENT_MASK);
        }


        /**
            Sets the displayed text of this InputComponent to the
            names of the mapped keys.
        */
        private void setText() {
            String text = "";
            List list = inputManager.getMaps(action);
            if (list.size() > 0) {
                for (int i=0; i<list.size(); i++) {
                    text+=(String)list.get(i) + ", ";
                }
                // remove the last comma
                text = text.substring(0, text.length() - 2);
            }

            // make sure we don't get deadlock
            synchronized (getTreeLock()) {
                setText(text);
            }

        }


        /**
            Maps the GameAction for this InputComponent to the
            specified key or mouse action.
        */
        private void mapGameAction(int code, boolean isMouseMap) {
            if (inputManager.getMaps(action).size() >= 3) {
                inputManager.clearMap(action);
            }
            if (isMouseMap) {
                inputManager.mapToMouse(action, code);
            }
            else {
                inputManager.mapToKey(action, code);
            }
            resetInputs();
            screen.getFullScreenWindow().requestFocus();
        }

        private void resetInputs() {
            for (int i=0; i<inputs.size(); i++) {
                ((InputComponent)inputs.get(i)).setText();
            }
        }


        // alternative way to intercept key events
        @Override
        protected void processKeyEvent(KeyEvent e) {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                // if backspace is pressed, clear the map
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE &&
                    inputManager.getMaps(action).size() > 0)
                {
                    inputManager.clearMap(action);
                    setText("");
                    screen.getFullScreenWindow().requestFocus();
                }
                else {
                    mapGameAction(e.getKeyCode(), false);
                }
            }
            e.consume();
        }


        // alternative way to intercept mouse events
        @Override
        protected void processMouseEvent(MouseEvent e) {
            if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                if (hasFocus()) {
                    int code = InputManager.getMouseButtonCode(e);
                    mapGameAction(code, true);
                }
                else {
                    requestFocus();
                }
            }
            e.consume();
        }


        // alternative way to intercept mouse events
        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            e.consume();
        }


        // alternative way to intercept mouse events
        @Override
        protected void processMouseWheelEvent(MouseWheelEvent e) {
            if (hasFocus()) {
                int code = InputManager.MOUSE_WHEEL_DOWN;
                if (e.getWheelRotation() < 0) {
                    code = InputManager.MOUSE_WHEEL_UP;
                }
                mapGameAction(code, true);
            }
            e.consume();
        }
    }

}

