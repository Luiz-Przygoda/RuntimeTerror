package MeleeLearning;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.HashMap;

/**
 * TrainerBot - O Estudante.
 * Possui todas as habilidades "físicas" do MasterBot, mas seu cérebro toma decisões
 * experimentais e recebe recompensas/punições para ajustar a Q-Table.
 */
public class TrainerBot extends AdvancedRobot {

    private static QBrain brain;
    private static final String BRAIN_FILE = "brain.dat";

    // Definição Rigorosa das Ações
    public static final int ACTION_ANTIGRAVITY = 0; // Esquiva inteligente
    public static final int ACTION_RUSHDOWN = 1;    // Ataque agressivo preditivo
    public static final int ACTION_CAMP_CORNER = 2; // Defesa de canto
    public static final int ACTION_SPIN_RADAR = 3;  // Passividade/Regeneração
    public static final int TOTAL_ACTIONS = 4;

    // Sensores e Estado
    private HashMap<String, EnemyBot> enemies = new HashMap<>();
    private static final double WALL_STICK = 140; // Margem de segurança da parede
    private String currentState;
    private int currentAction;
    private double currentReward = 0;

    // Classe Interna para Rastreamento (Idêntica ao MasterBot)
    private static class EnemyBot {
        Point2D.Double pos = new Point2D.Double();
        double heading;
        double velocity;
        double energy;
        long lastSeenTime;
    }

    public void run() {
        if (brain == null) {
            brain = new QBrain(TOTAL_ACTIONS);
            try { brain.load(getDataFile(BRAIN_FILE)); } catch (Exception e) {}
        }

        // Cores de Treino (Laranja/Vermelho para indicar perigo/aprendizado)
        setBodyColor(Color.ORANGE);
        setGunColor(Color.RED);
        setRadarColor(Color.RED);
        setBulletColor(Color.YELLOW);
        setScanColor(Color.RED);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        while (true) {
            radarLogic(); // Mantém visão do campo
            
            currentState = getState();
            // TRUE = Modo Treino (Habilita exploração aleatória)
            currentAction = brain.getAction(currentState, true);
            
            executeAction(currentAction);
            
            // Executa alguns ticks para dar tempo da ação ter efeito
            // Se mudarmos de ideia muito rápido, o robô fica "vibrando"
            for(int i=0; i<15; i++) {
                radarLogic(); 
                executeAction(currentAction); // Continua executando a ação escolhida
                execute();
            }

            // Aprendizado
            String newState = getState();
            // Recompensa extra por sobreviver (pequena)
            currentReward += 0.1; 
            brain.learn(currentState, currentAction, currentReward, newState);
            
            currentReward = 0; // Reseta para o próximo ciclo
        }
    }

    // ===== SENSORES (STATE MACHINE) =====
    private String getState() {
        double minDist = Double.POSITIVE_INFINITY;
        int othersCount = 0;
        long currentTime = getTime();

        for (EnemyBot en : enemies.values()) {
            // Remove fantasmas > 2s
            if (currentTime - en.lastSeenTime > 60) continue;
            othersCount++;
            double d = en.pos.distance(getX(), getY());
            if (d < minDist) minDist = d;
        }
        if (minDist == Double.POSITIVE_INFINITY) minDist = 1000;

        String distStr = (minDist < 250) ? "CLOSE" : (minDist < 600) ? "MID" : "FAR";
        String energyStr = (getEnergy() > 60) ? "HIGH" : (getEnergy() > 25) ? "MID" : "LOW";
        String enemiesStr = (othersCount > 4) ? "CROWD" : (othersCount > 1) ? "FEW" : "DUEL";

        return distStr + "-" + energyStr + "-" + enemiesStr;
    }

    // ===== SISTEMA DE RECOMPENSAS =====
    @Override
    public void onBulletHit(BulletHitEvent e) {
        currentReward += 15.0; // Bom tiro!
    }
    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        currentReward -= 15.0; // Ai!
        // Punição extra se estiver parado no canto levando tiro
        if (currentAction == ACTION_CAMP_CORNER) currentReward -= 5.0;
    }
    @Override
    public void onHitWall(HitWallEvent e) {
        currentReward -= 10.0; // Parede é ruim
        setBack(50); // Desencalhar
    }
    @Override
    public void onBulletMissed(BulletMissedEvent e) {
        currentReward -= 1.0; // Desperdício de energia
    }
    @Override
    public void onDeath(DeathEvent event) {
        currentReward -= 50.0; // Morrer é o pior cenário
        saveData();
    }
    @Override
    public void onWin(WinEvent event) {
        currentReward += 50.0; // Ganhar é o objetivo
        saveData();
    }
    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        saveData();
    }
    private void saveData() {
        try { brain.save(getDataFile(BRAIN_FILE)); } catch (Exception e) {}
    }

    // ===== EXECUTOR DE AÇÕES "PRO" (Idêntico ao MasterBot) =====
    private void executeAction(int action) {
        EnemyBot target = getNearestEnemy();
        switch (action) {
            case ACTION_ANTIGRAVITY:
                doAntiGravityMove();
                if (target != null && getEnergy() > 15) smartFire(target, 1.5);
                break;
            case ACTION_RUSHDOWN:
                doRushdownMove();
                break;
            case ACTION_CAMP_CORNER:
                doGoToCorner();
                if (target != null) smartFire(target, 2.0);
                break;
            case ACTION_SPIN_RADAR:
                setAhead(0); setTurnRight(0);
                if (target != null) smartFire(target, 1.0);
                break;
        }
    }

    // --- Lógicas de Movimento Avançadas (Copiadas do MasterBot) ---
    private void doAntiGravityMove() {
        double xForce = 0, yForce = 0;
        double myX = getX(), myY = getY();
        for (EnemyBot en : enemies.values()) {
            if (getTime() - en.lastSeenTime > 40) continue;
            double absBearing = Math.atan2(en.pos.y - myY, en.pos.x - myX);
            double dist = en.pos.distance(myX, myY);
            double force = -1200 / (dist * dist); 
            xForce += Math.sin(absBearing) * force;
            yForce += Math.cos(absBearing) * force;
        }
        xForce -= 5000 / Math.pow(myX, 3); 
        xForce += 5000 / Math.pow(getBattleFieldWidth() - myX, 3); 
        yForce -= 5000 / Math.pow(myY, 3); 
        yForce += 5000 / Math.pow(getBattleFieldHeight() - myY, 3); 
        goTo(wallSmoothing(myX, myY, Math.atan2(xForce, yForce), 1));
    }

    private void doRushdownMove() {
        EnemyBot target = getNearestEnemy();
        if (target != null) {
            double absBearing = Math.atan2(target.pos.y - getY(), target.pos.x - getX());
            double angle = absBearing + (Math.random() > 0.5 ? 0.4 : -0.4);
            goTo(wallSmoothing(getX(), getY(), angle, 1));
            smartFire(target, 3.0); // Tiro máximo
        } else {
            setTurnRight(20); 
        }
    }
    
    private void doGoToCorner() {
        double w = getBattleFieldWidth(), h = getBattleFieldHeight();
        double[][] corners = {{20, 20}, {20, h-20}, {w-20, 20}, {w-20, h-20}};
        double minDist = Double.POSITIVE_INFINITY;
        double tx = 0, ty = 0;
        for(double[] p : corners) {
            double d = Point2D.distance(getX(), getY(), p[0], p[1]);
            if(d < minDist) { minDist = d; tx = p[0]; ty = p[1]; }
        }
        goTo(Math.atan2(ty - getY(), tx - getX()));
    }

    private void smartFire(EnemyBot target, double power) {
        if (target == null) return;
        if (getEnergy() < 10) power = 0.5;
        double bulletSpeed = 20 - 3 * power;
        double nextX = target.pos.x, nextY = target.pos.y;
        for (int i = 0; i < 15; i++) {
            double dist = Point2D.distance(getX(), getY(), nextX, nextY);
            double timeToHit = dist / bulletSpeed;
            nextX = target.pos.x + Math.sin(target.heading) * target.velocity * timeToHit;
            nextY = target.pos.y + Math.cos(target.heading) * target.velocity * timeToHit;
            nextX = Math.max(18, Math.min(getBattleFieldWidth() - 18, nextX));
            nextY = Math.max(18, Math.min(getBattleFieldHeight() - 18, nextY));
        }
        double absBearing = Math.atan2(nextY - getY(), nextX - getX());
        setTurnGunRightRadians(Utils.normalRelativeAngle(absBearing - getGunHeadingRadians()));
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) fire(power);
    }

    private void radarLogic() {
        double radarTurn = Double.POSITIVE_INFINITY;
        long maxTime = 0;
        for (EnemyBot en : enemies.values()) {
            if (getTime() - en.lastSeenTime > maxTime) {
                maxTime = getTime() - en.lastSeenTime;
                double absBearing = Math.atan2(en.pos.y - getY(), en.pos.x - getX());
                radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
            }
        }
        setTurnRadarRightRadians(radarTurn + (radarTurn < 0 ? -0.2 : 0.2));
    }

    private double wallSmoothing(double x, double y, double angle, int orientation) {
        double testX = x + Math.sin(angle) * WALL_STICK;
        double testY = y + Math.cos(angle) * WALL_STICK;
        if (testX > 18 && testX < getBattleFieldWidth() - 18 && testY > 18 && testY < getBattleFieldHeight() - 18) 
            return angle;
        return wallSmoothing(x, y, angle + 0.05 * orientation, orientation);
    }

    private void goTo(double angle) {
        double dist = 150; 
        double turnAngle = Utils.normalRelativeAngle(angle - getHeadingRadians());
        if (Math.abs(turnAngle) > Math.PI / 2) {
            turnAngle = Utils.normalRelativeAngle(turnAngle + Math.PI);
            setBack(dist);
        } else {
            setAhead(dist);
        }
        setTurnRightRadians(turnAngle);
    }
    
    private EnemyBot getNearestEnemy() {
        double minDist = Double.POSITIVE_INFINITY;
        EnemyBot target = null;
        for (EnemyBot en : enemies.values()) {
            if (getTime() - en.lastSeenTime > 60) continue;
            double d = en.pos.distance(getX(), getY());
            if (d < minDist) { minDist = d; target = en; }
        }
        return target;
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        String name = e.getName();
        EnemyBot en = enemies.get(name);
        if (en == null) { en = new EnemyBot(); enemies.put(name, en); }
        double absBearing = getHeadingRadians() + e.getBearingRadians();
        en.pos.x = getX() + e.getDistance() * Math.sin(absBearing);
        en.pos.y = getY() + e.getDistance() * Math.cos(absBearing);
        en.energy = e.getEnergy();
        en.heading = e.getHeadingRadians();
        en.velocity = e.getVelocity();
        en.lastSeenTime = getTime();
    }
    @Override
    public void onRobotDeath(RobotDeathEvent e) { enemies.remove(e.getName()); }
}