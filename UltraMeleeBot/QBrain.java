package MeleeLearning;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * QBrain - Implementação simples de Q-Learning.
 * 
 * Mantém uma tabela: estado(String) -> vetor de Q-values (double[numActions]).
 */
public class QBrain {

    private final HashMap<String, double[]> qTable;
    private final int numActions;
    private final Random random;

    // Hiperparâmetros
    private static final double ALPHA   = 0.1;  // taxa de aprendizado
    private static final double GAMMA   = 0.9;  // desconto futuro
    private static final double EPSILON = 0.2;  // taxa de exploração

    public QBrain(int numActions) {
        this.numActions = numActions;
        this.qTable = new HashMap<>();
        this.random = new Random();
    }

    // Garante que o estado existe na tabela
    private double[] getQRow(String state) {
        return qTable.computeIfAbsent(state, s -> new double[numActions]);
    }

    public int getBestAction(String state) {
        double[] row = getQRow(state);
        int best = 0;
        double bestVal = row[0];
        for (int i = 1; i < numActions; i++) {
            if (row[i] > bestVal) {
                bestVal = row[i];
                best = i;
            }
        }
        return best;
    }

    public int getAction(String state, boolean training) {
        if (training && random.nextDouble() < EPSILON) {
            // Exploração
            return random.nextInt(numActions);
        }
        // Exploitation
        return getBestAction(state);
    }

    /**
     * Atualização Q-Learning (equação de Bellman).
     *
     * oldState: estado anterior
     * action  : ação tomada nesse estado
     * reward  : recompensa acumulada
     * newState: próximo estado (ou null se for estado terminal)
     */
    public void learn(String oldState, int action, double reward, String newState) {
        if (oldState == null) return;

        double[] oldRow = getQRow(oldState);

        double maxFutureQ;
        if (newState == null) {
            // Estado terminal: não há valor futuro
            maxFutureQ = 0.0;
        } else {
            double[] newRow = getQRow(newState);
            maxFutureQ = newRow[0];
            for (int i = 1; i < numActions; i++) {
                if (newRow[i] > maxFutureQ) {
                    maxFutureQ = newRow[i];
                }
            }
        }

        double currentQ = oldRow[action];
        double updated = currentQ + ALPHA * (reward + GAMMA * maxFutureQ - currentQ);
        oldRow[action] = updated;
    }

    public void save(File file) {
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(qTable);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public void load(File file) {
        try {
            if (!file.exists()) return;
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Object o = ois.readObject();
                if (o instanceof Map) {
                    qTable.clear();
                    qTable.putAll((Map<String, double[]>) o);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}