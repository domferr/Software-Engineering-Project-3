package ie.ucdconnect.sep;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * This class reads the testcases and generates a random solution.
 * It saves each solution into a file inside the testcase dir.
 * Each row of the solution file has a project and the assigned student's ID.
 */
public class SolutionGenerator implements Default{

	// The number of generations to be run before a solution is returned.
	private static final int NUM_GENERATIONS = 10000;
	// The number of solutions in each generation.
	private static final int GENERATION_SIZE = 250;
	// The number of "bad" solutions that will be removed at the end of each generation.
	private static final int GENERATION_CULL = 150;
	// The number of iterations that SA algorithm will do before lowering the temperature
	private static final double MINIMA_NUM_ITERATIONS = 100;
	// How much the starting temperature should be higher than the maxEnergyDelta
	private static final double MAX_TEMPERATURE_INCREASE = 10;

	private static Random random;

	public static void main(String[] args) throws IOException {
		random = new Random(System.currentTimeMillis());

		int[] testSetsStudentsSize = Config.getInstance().getTestSetsStudentsSize();

		int test_size = testSetsStudentsSize[1];

		//Read test set
		List<StaffMember> staffMembers = Utils.readStaffMembers();
		List<Project> projects = Utils.readProjects(staffMembers, test_size);
		List<Student> students = Utils.readStudents(Utils.generateProjectsMap(projects), test_size);

		//Run genetic algorithm
		//Solution solution = runGeneticAlgorithm(projects, students);
		Solution solution = runSimulatedAnnealingAlgorithm(projects, students);
		System.out.println("Final energy: " + solution.getEnergy() + ". Final fitness: " + solution.getFitness() + ".");

		//Save generated solution into resources dir
		saveSolution(solution, test_size);
	}

	/**
	 * Static method that takes a list of projects and students and then generates one random solution.
	 */
	public static Solution createOneRandomSolution(List<Project> projects, List<Student> students) {
		ImmutableMultimap.Builder<Project, Student> mapBuilder = ImmutableMultimap.builder();

		List<Student> studentsCopy = new ArrayList<>(students);
		Collections.shuffle(studentsCopy);

		for (Student student : studentsCopy) {
			int randomIndex = random.nextInt(projects.size());
			mapBuilder.put(projects.get(randomIndex), student);
		}

		return Solution.SolutionFactory.createAndEvaluate(mapBuilder.build(), GPA_IMPORTANCE);
	}

	/**
	 * Runs the genetic algorithm and returns the best result.
	 */
	private static Solution runGeneticAlgorithm(List<Project> projects, List<Student> students) {
		List<Solution> solutions = getRandomSolutionList(projects, students, GENERATION_SIZE);

		for (int i = 0; i < NUM_GENERATIONS; i++) {
			System.out.println("Running generation: " + i);
			//Screen solutions and remove the "bad" ones. GENERATION_CULL is the number of removed solutions
			solutions = SolutionAcceptor.screenSolutions(solutions, GENERATION_CULL);
			solutions = mutate(solutions, projects);
		}

		return solutions.get(0);
	}

	/**
	 * Runs the simulated algorithm and returns the best result.
	 */
	private static Solution runSimulatedAnnealingAlgorithm(List<Project> projects, List<Student> students) {
		//Calculate max energy delta in 100 random solutions
		double maxEnergyDelta = calculateMaxEnergyDelta(getRandomSolutionList(projects, students, 100));
		//Set the initial temperature as higher than MaxEnergyDelta
		double temperature = maxEnergyDelta + (MAX_TEMPERATURE_INCREASE * random.nextDouble());
		//Generate one random currentSolution
		Solution currentSolution = createOneRandomSolution(projects, students);
		Solution bestSolution = currentSolution;
		while(temperature > 0) {
			//Find a local minima
			for (int i = 0; i < MINIMA_NUM_ITERATIONS; i++) {
				Solution mutatedSolution = mutate(currentSolution, projects);
				double deltaEnergy = Math.abs(mutatedSolution.getEnergy() - currentSolution.getEnergy());
				//Accepting if the changes have better energy
				if (mutatedSolution.getEnergy() < currentSolution.getEnergy())
					currentSolution = mutatedSolution;
				//Otherwise accept with Boltzmann probability
				else if (random.nextDouble() <= Math.exp(-deltaEnergy/temperature))
					currentSolution = mutatedSolution;
			}
			//Remember the global minima
			if (currentSolution.getEnergy() < bestSolution.getEnergy())
				bestSolution = currentSolution;
			//Lower the temperature based on how much high it is
			temperature -= temperature*random.nextDouble(); //The higher it is the more it is lowered
		}

		return bestSolution;
	}

	/** Returns the max energy delta calculated from the given list of solutions */
	private static double calculateMaxEnergyDelta(List<Solution> solutions) {
		double maxDelta = 0;

		for (Solution thisSolution :solutions) {
			for (Solution otherSolution :solutions) {
				if (!thisSolution.equals(otherSolution)) {
					double delta = Math.abs(thisSolution.getEnergy() - otherSolution.getEnergy());
					if (delta > maxDelta)
						maxDelta = delta;
				}
			}
		}

		return maxDelta;
	}

	private static List<Solution> mutate(List<Solution> solutions, List<Project> projects) {
		List<Solution> newSolutions = new ArrayList<>(solutions);
		while (newSolutions.size() < GENERATION_SIZE) {
			Solution randomSolution = solutions.get(random.nextInt(solutions.size()));
			newSolutions.add(mutate(randomSolution, projects));
		}

		return newSolutions;
	}

	private static Solution mutate(Solution solution, List<Project> projects) {
		ImmutableCollection<Map.Entry<Project, Student>> entries = solution.getEntries();
		ImmutableMultimap.Builder<Project, Student> mapBuilder = ImmutableMultimap.builder();
		int randomIndex = random.nextInt(entries.size());
		int index = 0;
		for (Map.Entry<Project, Student> entry : entries) {
			if (index == randomIndex) {
				Project newProject = projects.get(random.nextInt(projects.size()));
				mapBuilder.put(newProject, entry.getValue());
			} else {
				mapBuilder.put(entry);
			}
			index++;
		}

		return Solution.SolutionFactory.createAndEvaluate(mapBuilder.build(), GPA_IMPORTANCE);
	}

	/** Returns a list of random solutions */
	public static List<Solution> getRandomSolutionList(List<Project> projects, List<Student> students, int howMany) {
		List<Solution> solutions = new ArrayList<>();
		//Create a list of solutions and find the max energy delta
		for (int i = 0; i < howMany; i++) {
			solutions.add(createOneRandomSolution(projects, students));
		}
		return solutions;
	}

	/**
	 * Write the given solution into a file.
	 */
	private static void saveSolution(Solution solution, int size) throws IOException {
		String dirName = Config.getInstance().getTestcaseDirName();
		File testCaseDir = new File(dirName);
		if (!testCaseDir.exists())
			testCaseDir.mkdir();

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(dirName + "solutionFor" + size + "Students.csv"));
			writer.write(solution.toCSV());
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
