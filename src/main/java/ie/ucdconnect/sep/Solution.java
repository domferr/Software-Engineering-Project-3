package ie.ucdconnect.sep;

import com.google.common.collect.ImmutableMultimap;
import com.opencsv.CSVParser;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** This class represent a solution. It maps each project to a student */
public class Solution {

	private ImmutableMultimap<Project, Student> projectMapping;

	public Solution(ImmutableMultimap<Project, Student> projectMapping) {
		this.projectMapping = projectMapping;
	}

	/** Static method that takes a list of projects and students and then generates a random solution.
	 *  The algorithm is the following:
	 *  Assign every student their LEAST preferable project, if that project isn't available, try
	 *  to give them more preferable projects. Any student who can not be matched to a project
	 *  (all their preferences are already taken) will be assigned a random project from the list. */
	public static Solution createRandom(List<Project> projects, List<Student> students) {
		ImmutableMultimap.Builder<Project, Student> mapBuilder = ImmutableMultimap.builder();
		Random rand = new Random();

		List<Student> studentsCopy = new ArrayList<>(students);
		Collections.shuffle(studentsCopy);

		for (Student student : studentsCopy) {
			int randomIndex = rand.nextInt(projects.size());
			mapBuilder.put(projects.get(randomIndex), student);
		}
		return new Solution(mapBuilder.build());
	}


	/** Returns the project assigned to a given student. Returns null if the student has no project assigned */
	public Project getAssignedProject(Student student) {
		for (Project project : projectMapping.keySet()) {
			if (projectMapping.get(project).contains(student))
				return project;
		}
		return null;
	}

	/** Returns the student assigned to a given project. Returns null if the project has no student assigned */
	public Collection<Student> getAssignedStudents(Project project) {
		return projectMapping.get(project);
	}

	public Set<Project> getProjects() {
		return projectMapping.keySet();
	}

	public ArrayList<Student> getStudents() {
		return new ArrayList<Student>(projectMapping.values());
	}

	/** Returns this solution in CSV format.
	 *  Each file's row has two columns. The first column is the project
	 *  while the second one is the assigned student. */
	public String toCSV() {
		StringBuilder s = new StringBuilder();
		for (Project project: projectMapping.keySet()) {
			String studentNumbers = projectMapping.get(project).stream().map(Student::getStudentNumber).collect(Collectors.joining(","));
			s.append(project.getTitle()).append(",\"").append(studentNumbers).append("\"\n");
		}
		return s.toString();
	}

	/** Returns the solution from a given csvfile content.
	 * @throws IllegalStateException if a student cannot be mapped
	 * @throws IOException if an error occurs while parsing the CSV
	 */
	public static Solution fromCSV(String csvFile, List<Student> students, Map<String, Project> projectsMap) throws IllegalStateException, IOException {
		CSVParser csvParser = new CSVParser();
		ImmutableMultimap.Builder<Project, Student> mapBuilder = ImmutableMultimap.builder();
		String[] rows = csvFile.split("\n");
		for (String row : rows) {
			String[] columns = csvParser.parseLine(row);
			if (columns.length != 2)
				throw new IllegalArgumentException("The row ["+ row +"] must have two columns");
			Project project = projectsMap.get(columns[0]);
			String[] studentIds = columns[1].split(",");
			List<Student> projectStudents = findStudents(studentIds, students);
			for (Student projectStudent : projectStudents) {
				mapBuilder.put(project, projectStudent);
			}
		}
		return new Solution(mapBuilder.build());
	}

	private static List<Student> findStudents(String[] studentIds, List<Student> students) {
		LinkedList<Student> returnedStudents = new LinkedList<>();
		for (Student student : students) {
			for (String studentNumber : studentIds) {
				if (student.getStudentNumber().equals(studentNumber)) {
					returnedStudents.add(student);
				}
			}
		}
		return returnedStudents;
	}


	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (Project project: projectMapping.keySet()){
			s.append("Project: ").append(project.getTitle()).append(" -> Student: ").append(projectMapping.get(project).toString()).append("\n");
		}
		return s.toString();
	}
}
