package MeleeLearning;

import robocode.*;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

/**
 * UltraMeleeBot
 *
 * Robô híbrido:
 * - Estratégias fortes hard-coded (anti-gravidade, rush, sniper, crazy)
 * - Q-Learning decide QUAL estratégia usar para cada estado do campo.
 *
 * Use com o QBrain da resposta anterior (que aceita newState == null).
 */
public class UltraMeleeBot extends AdvancedRobot {

    // AÇÕES / ESTRATÉGIAS
    private static final int ACTION_EVASIVE  = 0; // Anti-gravidade (foge do risco)
    private static final int ACTION_AGGRESS  = 1; // Rush no inimigo (finalizar)
    private static final int ACTION_SNIPER   = 2; // Kiting / médio-longo alcance
    private static final int ACTION_CRAZY    = 3; // Padrão caótico pra confundir mira
    private static final int NUM_ACTIONS     = 4;

    private static final String BRAIN_FILE = "ultra-brain.dat";
    private static QBrain brain;

    // Controle do Q-Learning
    private String lastState = null;
    private int lastAction = 0;
    private double rewardAcc = 0.0;

    // Modo treino: se quiser "modo campeonato", pode pôr false
    private static final boolean TRAINING = true;

    // Info de inimigos
    private static class Enemy {
        String name;
        Point2D.Double pos = new Point2D.Double();
        double energy;
        double heading;
        double velocity;
        long lastSeen;
    }

    private final Map<String, Enemy> enemies = new HashMap<>();

    // Movimento auxiliar
    private int crazyDir = 1;

    @Override
    public void run() {
        if (brain == null) {
            brain = new QBrain(NUM_ACTIONS);
            try {
                brain.load(getDataFile(BRAIN_FILE));
            } catch (Exception ignored) {}
        }

        // Aparência
        setBodyColor(new Color(255, 140, 0)); // Laranja forte
        setGunColor(Color.RED);
        setRadarColor(Color.WHITE);
        setBulletColor(Color.YELLOW);
        setScanColor(Color.GREEN);

        // Desacoplar movimentos
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        // Radar girando sem parar até travar
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

        while (true) {
            // Estado atual
            String currentState = buildState();

            // Atualiza Q com passo anterior
            if (lastState != null) {
                brain.learn(lastState, lastAction, rewardAcc, currentState);
                rewardAcc = 0.0;
            }

            int action = brain.getAction(currentState, TRAINING);
            lastState = currentState;
            lastAction = action;

            // Mantém a MESMA estratégia por alguns ticks, pra evitar "vibração"
            for (int i = 0; i < 20; i++) {
                doRadarLock();
                executeStrategy(action);
                rewardAcc += 0.03; // recompensa de sobrevivência leve
                execute();
            }
        }
    }

    // =================== ESTADO ===================

    private String buildState() {
        double minDist = 1e9;
        int count = 0;
        long now = getTime();

        for (Enemy e : enemies.values()) {
            if (now - e.lastSeen > 40) continue;
            count++;
            double d = e.pos.distance(getX(), getY());
            if (d < minDist) minDist = d;
        }
        if (minDist == 1e9) minDist = 1000;

        String distStr =
                (minDist < 150) ? "VERY_CLOSE" :
                (minDist < 300) ? "CLOSE" :
                (minDist < 600) ? "MID" : "FAR";

        String energyStr =
                (getEnergy() > 70) ? "HIGH" :
                (getEnergy() > 30) ? "MID" : "LOW";

        String enemiesStr =
                (count >= 5) ? "MANY" :
                (count >= 3) ? "FEW" :
                (count >= 1) ? "DUEL" : "ALONE";

        double dx = Math.min(getX(), getBattleFieldWidth() - getX());
        double dy = Math.min(getY(), getBattleFieldHeight() - getY());
        double wallDist = Math.min(dx, dy);
        String wallStr = (wallDist < 80) ? "WALL" : "CENTER";

        return distStr + "|" + energyStr + "|" + enemiesStr + "|" + wallStr;
    }

    // =================== RADAR ===================

    private void doRadarLock() {
        Enemy t = getBestTarget();
        if (t == null) {
            if (getRadarTurnRemainingRadians() == 0.0) {
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            }
            return;
        }
        double absBearing = angleTo(t);
        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        // "Overshoot" leve para não perder lock
        setTurnRadarRightRadians(radarTurn * 2);
    }

    // =================== ESTRATÉGIAS ===================

    private void executeStrategy(int action) {
        switch (action) {
            case ACTION_EVASIVE:
                strategyEvasive();
                break;
            case ACTION_AGGRESS:
                strategyAggressive();
                break;
            case ACTION_SNIPER:
                strategySniper();
                break;
            case ACTION_CRAZY:
                strategyCrazy();
                break;
        }
    }

    private Enemy getBestTarget() {
        // alvo principal: inimigo mais perto e ainda "vivo" recentemente
        Enemy best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        long now = getTime();

        for (Enemy e : enemies.values()) {
            if (now - e.lastSeen > 40) continue;
            double d = e.pos.distance(getX(), getY());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private double angleTo(Enemy e) {
        double dx = e.pos.x - getX();
        double dy = e.pos.y - getY();
        return Math.atan2(dx, dy); // padrão usado antes: x = sin, y = cos
    }

    private void turnToAngle(double angle) {
        double turn = Utils.normalRelativeAngle(angle - getHeadingRadians());
        setTurnRightRadians(turn);
    }

    private void goToAngle(double angle, double distance) {
        double turn = Utils.normalRelativeAngle(angle - getHeadingRadians());
        if (Math.abs(turn) > Math.PI / 2) {
            turn = Utils.normalRelativeAngle(turn + Math.PI);
            setBack(distance);
        } else {
            setAhead(distance);
        }
        setTurnRightRadians(turn);
    }

    private void aimAndFirePredictive(Enemy e, double desiredPower) {
        if (e == null) return;

        // Ajuste de power com base em energia e distância
        double dist = e.pos.distance(getX(), getY());
        double power = desiredPower;

        if (getEnergy() < 20) power = Math.min(power, 1.5);
        if (dist > 500) power = Math.min(power, 1.5);
        if (dist > 750) power = Math.min(power, 1.0);

        power = Math.max(0.1, Math.min(3.0, power));

        double bulletSpeed = 20 - 3 * power;
        double predX = e.pos.x;
        double predY = e.pos.y;

        // Itera algumas vezes pra achar ponto de interceptação
        for (int i = 0; i < 15; i++) {
            double time = Point2D.distance(getX(), getY(), predX, predY) / bulletSpeed;
            predX = e.pos.x + Math.sin(e.heading) * e.velocity * time;
            predY = e.pos.y + Math.cos(e.heading) * e.velocity * time;

            // Clamping dentro do campo
            predX = Math.max(18, Math.min(getBattleFieldWidth() - 18, predX));
            predY = Math.max(18, Math.min(getBattleFieldHeight() - 18, predY));
        }

        double fireAngle = Math.atan2(predX - getX(), predY - getY());
        double gunTurn = Utils.normalRelativeAngle(fireAngle - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemainingRadians()) < Math.toRadians(8)) {
            fire(power);
        }
    }

    // --------- Estratégia 0: Evasivo (Anti-gravidade) ---------
    private void strategyEvasive() {
        double xForce = 0;
        double yForce = 0;
        double myX = getX();
        double myY = getY();
        long now = getTime();

        for (Enemy e : enemies.values()) {
            if (now - e.lastSeen > 50) continue;
            double dx = e.pos.x - myX;
            double dy = e.pos.y - myY;
            double dist2 = dx * dx + dy * dy;
            if (dist2 < 1) dist2 = 1;
            double force = -6000 / dist2; // força repulsiva

            double angle = Math.atan2(dx, dy);
            xForce += Math.sin(angle) * force;
            yForce += Math.cos(angle) * force;
        }

        // Repulsão das paredes
        double w = getBattleFieldWidth();
        double h = getBattleFieldHeight();
        double margin = 40;

        xForce += 5000 / Math.pow(myX - margin, 3);
        xForce -= 5000 / Math.pow(w - myX - margin, 3);
        yForce += 5000 / Math.pow(myY - margin, 3);
        yForce -= 5000 / Math.pow(h - myY - margin, 3);

        double moveAngle = Math.atan2(xForce, yForce);
        goToAngle(moveAngle, 120);

        Enemy t = getBestTarget();
        if (t != null) {
            aimAndFirePredictive(t, 1.8);
        }
    }

    // --------- Estratégia 1: Agressivo (Rush) ---------
    private void strategyAggressive() {
        Enemy t = getBestTarget();
        if (t == null) {
            strategyEvasive();
            return;
        }

        double angle = angleTo(t);
        // Pequeno offset lateral pra não comer bala reta
        double offset = (Math.random() < 0.5 ? 1 : -1) * 0.5;
        angle += offset;

        goToAngle(angle, 160);
        aimAndFirePredictive(t, 3.0);
    }

    // --------- Estratégia 2: Sniper / Kiting ---------
    private void strategySniper() {
        Enemy t = getBestTarget();
        if (t == null) {
            strategyEvasive();
            return;
        }

        double dist = t.pos.distance(getX(), getY());
        double desired = 450; // distância ideal

        double angle;
        if (dist < 300) {
            // muito perto -> recua
            angle = angleTo(t) + Math.PI;
        } else if (dist > 600) {
            // muito longe -> aproxima
            angle = angleTo(t);
        } else {
            // faixa ok -> orbita
            angle = angleTo(t) + Math.PI / 2 * (Math.random() < 0.5 ? 1 : -1);
        }

        double moveDist = 140 + Math.random() * 40;
        goToAngle(angle, moveDist);
        aimAndFirePredictive(t, 2.0);
    }

    // --------- Estratégia 3: Crazy ---------
    private void strategyCrazy() {
        if (Math.random() < 0.1) {
            crazyDir *= -1;
        }
        setTurnRight(45 * crazyDir);
        setAhead(120 * crazyDir);

        Enemy t = getBestTarget();
        if (t != null) {
            aimAndFirePredictive(t, 1.7);
        }
    }

    // =================== EVENTOS / RECOMPENSAS ===================

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        Enemy enemy = enemies.get(e.getName());
        if (enemy == null) {
            enemy = new Enemy();
            enemy.name = e.getName();
            enemies.put(e.getName(), enemy);
        }

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        double dist = e.getDistance();

        enemy.pos.x = getX() + Math.sin(absBearing) * dist;
        enemy.pos.y = getY() + Math.cos(absBearing) * dist;
        enemy.energy = e.getEnergy();
        enemy.heading = e.getHeadingRadians();
        enemy.velocity = e.getVelocity();
        enemy.lastSeen = getTime();
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        enemies.remove(event.getName());
        // matar inimigo indiretamente (último dano foi nosso) é difícil detectar,
        // então deixamos a recompensa principal nos eventos de tiro.
    }

    @Override
    public void onBulletHit(BulletHitEvent event) {
        rewardAcc += 20.0; // tiro bom
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        rewardAcc -= 15.0; // tomou bala
    }

    @Override
    public void onBulletMissed(BulletMissedEvent event) {
        rewardAcc -= 2.0; // desperdiçou tiro
    }

    @Override
    public void onHitWall(HitWallEvent event) {
        rewardAcc -= 20.0;
        setBack(80);
    }

    @Override
    public void onHitRobot(HitRobotEvent event) {
        // encostar em inimigo normalmente é ruim (rushing cego)
        rewardAcc -= 8.0;
    }

    @Override
    public void onWin(WinEvent event) {
        rewardAcc += 80.0;
        if (lastState != null) {
            brain.learn(lastState, lastAction, rewardAcc, null); // estado terminal
        }
        saveBrain();
    }

    @Override
    public void onDeath(DeathEvent event) {
        rewardAcc -= 80.0;
        if (lastState != null) {
            brain.learn(lastState, lastAction, rewardAcc, null); // estado terminal
        }
        saveBrain();
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        saveBrain();
    }

    private void saveBrain() {
        try {
            brain.save(getDataFile(BRAIN_FILE));
        } catch (Exception ignored) {}
    }
}