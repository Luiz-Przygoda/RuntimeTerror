package MeleeLearning;

import java.io.*;
import java.util.HashMap;
import java.util.Random;

/**
 * QBrain - Núcleo de Aprendizado por Reforço (Q-Learning).
 * Gerencia a tabela Q (Estado -> Ação -> Valor).
 */
public class QBrain {
    
    // Tabela Q: Mapeia uma String de Estado para um array de valores (um para cada ação)
    private HashMap<String, double[]> qTable;
    
    // Hiperparâmetros
    // ALPHA: Taxa de aprendizado. 0.1 é seguro para não oscilar demais.
    private static final double ALPHA = 0.1;  
    // GAMMA: Fator de desconto. 0.9 valoriza recompensas futuras (sobrevivência a longo prazo).
    private static final double GAMMA = 0.9;  
    // EPSILON: Taxa de exploração. 20% das vezes o Trainer fará algo aleatório para testar.
    private static final double EPSILON = 0.2; 
    
    private int numActions;
    private Random random;

    public QBrain(int numberOfActions) {
        this.numActions = numberOfActions;
        this.qTable = new HashMap<>();
        this.random = new Random();
    }

    public int getBestAction(String state) {
        if (!qTable.containsKey(state)) {
            initNewState(state);
        }
        double[] actions = qTable.get(state);
        int bestAction = 0;
        double maxVal = actions[0];
        for (int i = 1; i < actions.length; i++) {
            if (actions[i] > maxVal) {
                maxVal = actions[i];
                bestAction = i;
            }
        }
        return bestAction;
    }

    public int getAction(String state, boolean isTraining) {
        // Se estiver treinando, às vezes escolhe aleatoriamente (Exploração)
        if (isTraining && random.nextDouble() < EPSILON) {
            return random.nextInt(numActions);
        }
        return getBestAction(state); // Exploitation
    }

    public void learn(String oldState, int actionTaken, double reward, String newState) {
        if (!qTable.containsKey(oldState)) initNewState(oldState);
        if (!qTable.containsKey(newState)) initNewState(newState);

        double[] oldQ = qTable.get(oldState);
        double[] newQ = qTable.get(newState);

        double maxFutureQ = -Double.MAX_VALUE;
        for (double v : newQ) {
            if (v > maxFutureQ) maxFutureQ = v;
        }

        double currentQ = oldQ[actionTaken];
        // Equação de Bellman para Q-Learning
        oldQ[actionTaken] = currentQ + ALPHA * (reward + GAMMA * maxFutureQ - currentQ);
    }

    private void initNewState(String state) {
        qTable.put(state, new double[numActions]);
    }

    public void save(File file) {
        try {
            // Garante que o diretório pai exista
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
            oos.writeObject(qTable);
            oos.close();
            System.out.println("Cérebro salvo: " + file.getAbsolutePath() + " (Estados: " + qTable.size() + ")");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public void load(File file) {
        try {
            if (!file.exists()) return;
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
            qTable = (HashMap<String, double[]>) ois.readObject();
            ois.close();
            System.out.println("Cérebro carregado! Estados: " + qTable.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}