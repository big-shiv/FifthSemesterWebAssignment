import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;
import javax.imageio.ImageIO;
import java.io.IOException;

public class FlappyBird extends JFrame {

    public FlappyBird() {
        setTitle("Flappy Bird");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel gamePanel = new GamePanel();
        add(gamePanel);
        pack(); // Sizes the frame so all its contents are at or above their preferred sizes

        setLocationRelativeTo(null); // Center the window
        setVisible(true);
    }

    public static void main(String[] args) {
        // Run the game on the Event Dispatch Thread (EDT) for thread safety
        SwingUtilities.invokeLater(FlappyBird::new);
    }
}

class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener {

    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    private enum GameState {
        SELECT_DIFFICULTY,
        PLAYING,
        GAME_OVER
    }

    // --- Game Dimensions ---
    private static final int SCREEN_WIDTH = 360;
    private static final int SCREEN_HEIGHT = 640;

    // --- Bird Properties ---
    private int birdX = 50;
    private int birdY = SCREEN_HEIGHT / 2;
    private int birdWidth = 34;
    private int birdHeight = 24;
    private int velocityY = 0;
    private final int gravity = 1;
    private final int jumpStrength = -12;
    private Rectangle bird;
    private Image birdImage;

    // --- Pipe Properties ---
    private ArrayList<Rectangle> pipes;
    private int pipeX = SCREEN_WIDTH;
    private int pipeWidth = 64;
    private int pipeGap;
    private int pipeSpeed;
    private Image pipeImage;
    private Image pipeImageFlipped;

    // --- Game State ---
    private Timer gameLoop;
    private int score = 0;
    private int highScore = 0;
    private Random random;
    private Difficulty difficulty;
    private int increaseDifficultyScore;
    private GameState gameState;

    public GamePanel() {
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(new Color(135, 206, 235)); // Sky blue background

        // Load images
        try {
            birdImage = ImageIO.read(getClass().getResource("bird.png"));
        } catch (IOException e) {
            System.err.println("Could not load bird image. Using a rectangle instead.");
            birdImage = null;
        }
        pipeImage = null;
        pipeImageFlipped = null;

        // Initialize game objects
        bird = new Rectangle(birdX, birdY, birdWidth, birdHeight);
        pipes = new ArrayList<>();
        random = new Random();

        // Setup listeners and timer
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);

        // Game loop timer, fires every 20ms
        gameLoop = new Timer(20, this);
        gameState = GameState.SELECT_DIFFICULTY;
        setDifficulty(Difficulty.EASY);
    }

    private BufferedImage makeColorTransparent(BufferedImage im, final Color color) {
        BufferedImage newImage = new BufferedImage(
            im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int i = 0; i < im.getWidth(); i++) {
            for (int j = 0; j < im.getHeight(); j++) {
                if (im.getRGB(i, j) != color.getRGB()) {
                    newImage.setRGB(i, j, im.getRGB(i, j));
                }
            }
        }
        return newImage;
    }

    private void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        switch (difficulty) {
            case EASY:
                pipeSpeed = 2;
                pipeGap = 200;
                increaseDifficultyScore = 10;
                break;
            case MEDIUM:
                pipeSpeed = 3;
                pipeGap = 150;
                increaseDifficultyScore = 5;
                break;
            case HARD:
                pipeSpeed = 4;
                pipeGap = 100;
                increaseDifficultyScore = 3;
                break;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    private void draw(Graphics g) {
        // Draw Bird
        if (birdImage != null) {
            g.drawImage(birdImage, bird.x, bird.y, bird.width, bird.height, null);
        } else {
            g.setColor(Color.ORANGE);
            g.fillRect(bird.x, bird.y, bird.width, bird.height);
        }

        // Draw Pipes
        g.setColor(Color.GREEN);
        for (Rectangle pipe : pipes) {
            if (pipeImage != null && pipeImageFlipped != null) {
                if (pipe.y == 0) {
                    g.drawImage(pipeImageFlipped, pipe.x, pipe.y, pipe.width, pipe.height, null);
                } else {
                    g.drawImage(pipeImage, pipe.x, pipe.y, pipe.width, pipe.height, null);
                }
            } else {
                g.fillRect(pipe.x, pipe.y, pipe.width, pipe.height);
            }
        }

        // Draw Score and UI text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 32));

        if (gameState == GameState.GAME_OVER) {
            g.drawString("Game Over", 100, SCREEN_HEIGHT / 2 - 50);
            g.drawString("Score: " + score, 115, SCREEN_HEIGHT / 2);
            g.drawString("High Score: " + highScore, 75, SCREEN_HEIGHT / 2 + 50);
            g.setFont(new Font("Arial", Font.PLAIN, 16));
            g.drawString("Click or Press Space to Restart", 70, SCREEN_HEIGHT / 2 + 100);
        } else if (gameState == GameState.SELECT_DIFFICULTY) {
            g.drawString("Select Difficulty", 50, SCREEN_HEIGHT / 2 - 100);
            g.drawRect(100, SCREEN_HEIGHT / 2 - 50, 160, 50);
            g.drawString("Easy", 150, SCREEN_HEIGHT / 2 - 15);
            g.drawRect(100, SCREEN_HEIGHT / 2 + 10, 160, 50);
            g.drawString("Medium", 130, SCREEN_HEIGHT / 2 + 45);
            g.drawRect(100, SCREEN_HEIGHT / 2 + 70, 160, 50);
            g.drawString("Hard", 150, SCREEN_HEIGHT / 2 + 105);
        } else if (gameState == GameState.PLAYING) {
            g.drawString(String.valueOf(score), SCREEN_WIDTH / 2 - 10, 50);
        }
    }

    private void move() {
        // Bird physics
        velocityY += gravity;
        bird.y += velocityY;
        bird.y = Math.max(bird.y, 0); // Prevent bird from going above the screen

        // Move pipes
        for (int i = 0; i < pipes.size(); i++) {
            Rectangle pipe = pipes.get(i);
            pipe.x -= pipeSpeed;
        }

        // Add new pipes
        if (!pipes.isEmpty() && pipes.get(0).x < -pipeWidth) {
            pipes.remove(0);
            pipes.remove(0); // Remove the pair
        }

        if (pipes.isEmpty() || pipes.get(pipes.size() - 1).x < SCREEN_WIDTH - 200) {
            addPipes();
        }

        // Check for score
        for (Rectangle pipe : pipes) {
            if (pipe.y == 0 && bird.x > pipe.x + pipe.width && pipe.width > 0) {
                 // A simple way to check if we passed the pipe, using width as a flag
                 score++;
                 pipe.width = 0; // Mark as passed to not score again
                 increaseDifficulty();
            }
        }
        
        // Check for collisions
        checkCollisions();
    }

    private void addPipes() {
        int pipeY = (int) (random.nextDouble() * (SCREEN_HEIGHT / 2.0)) + SCREEN_HEIGHT / 4;
        int topPipeHeight = pipeY - pipeGap / 2;
        int bottomPipeY = pipeY + pipeGap / 2;
        int bottomPipeHeight = SCREEN_HEIGHT - bottomPipeY;

        pipes.add(new Rectangle(SCREEN_WIDTH, 0, pipeWidth, topPipeHeight));
        pipes.add(new Rectangle(SCREEN_WIDTH, bottomPipeY, pipeWidth, bottomPipeHeight));
    }
    
    private void increaseDifficulty() {
        if (score > 0 && score % increaseDifficultyScore == 0) {
            pipeSpeed++;
        }
    }

    private void checkCollisions() {
        // Ground collision
        if (bird.y > SCREEN_HEIGHT - bird.height) {
            endGame();
        }
        // Pipe collision
        for (Rectangle pipe : pipes) {
            if (bird.intersects(pipe)) {
                endGame();
            }
        }
    }

    private void endGame() {
        gameState = GameState.GAME_OVER;
        gameLoop.stop();
        if (score > highScore) {
            highScore = score;
        }
    }

    private void restartGame() {
        bird.y = SCREEN_HEIGHT / 2;
        velocityY = 0;
        pipes.clear();
        score = 0;
        gameState = GameState.SELECT_DIFFICULTY;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == GameState.PLAYING) {
            move();
        }
        repaint(); // Redraws the screen
    }

    private void flap() {
        if (gameState == GameState.GAME_OVER) {
            restartGame();
        } else if (gameState == GameState.PLAYING) {
            velocityY = jumpStrength;
        }
    }
    
    // --- Input Handlers ---

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (gameState == GameState.SELECT_DIFFICULTY) {
                gameState = GameState.PLAYING;
                gameLoop.start();
            } else {
                flap();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        if (gameState == GameState.SELECT_DIFFICULTY) {
            int mx = e.getX();
            int my = e.getY();

            if (mx > 100 && mx < 260 && my > SCREEN_HEIGHT / 2 - 50 && my < SCREEN_HEIGHT / 2) {
                setDifficulty(Difficulty.EASY);
                gameState = GameState.PLAYING;
                gameLoop.start();
            } else if (mx > 100 && mx < 260 && my > SCREEN_HEIGHT / 2 + 10 && my < SCREEN_HEIGHT / 2 + 60) {
                setDifficulty(Difficulty.MEDIUM);
                gameState = GameState.PLAYING;
                gameLoop.start();
            } else if (mx > 100 && mx < 260 && my > SCREEN_HEIGHT / 2 + 70 && my < SCREEN_HEIGHT / 2 + 120) {
                setDifficulty(Difficulty.HARD);
                gameState = GameState.PLAYING;
                gameLoop.start();
            } else {
                gameState = GameState.PLAYING;
                gameLoop.start();
            }
        } else {
            flap();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}
}
