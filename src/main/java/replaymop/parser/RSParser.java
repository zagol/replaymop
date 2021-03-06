package replaymop.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.Scanner;

import javax.swing.text.html.HTMLDocument.HTMLReader.SpecialAction;

import replaymop.ReplayMOPException;
import replaymop.replayspecification.ReplaySpecification;
import replaymop.replayspecification.ScheduleUnit;
import replaymop.replayspecification.Variable;

public class RSParser extends Parser {

	
	Scanner input;
	

	boolean threadsDefined = false;

	public RSParser(File inputFile) throws Exception {
		if (!inputFile.getName().endsWith(".rs"))
			throw new Exception("expecting .rs file");
		input = new Scanner(inputFile);
		spec = new ReplaySpecification();
		this.spec.fileName = inputFile.getName().replace(".rs", "");
	}

	void handleThreads() throws ReplayMOPException {

		while (input.hasNext()) {
			long thread = input.nextLong();
			spec.threads.add(thread);
			String sep = input.next();
			if (sep.equals(";"))
				return;
			if (!sep.equals(","))
				throw new ReplayMOPException("expected ,");
		}
		throw new ReplayMOPException("expected ;");

	}

	void handleThreadCreationOrder() throws ReplayMOPException {

		while (input.hasNext()) {
			long thread = input.nextLong();
			if (!spec.threads.contains(thread))
				throw new ReplayMOPException("thread id undefined");
			spec.threadOrder.add(thread);
			String sep = input.next();
			if (sep.equals(";"))
				return;
			if (!sep.equals(","))
				throw new ReplayMOPException("expected ,");
		}
		throw new ReplayMOPException("expected ;");

	}

	void handleShared() throws ReplayMOPException {

		while (input.hasNext()) {
			Variable shared = new Variable();
			shared.type = input.next();
			if (shared.type.equals("all"))
				// spec.allShared = true;
				throw new ReplayMOPException("all: not supported yet");
			else
				shared.name = input.next();
			spec.shared.add(shared);
			String sep = input.next();
			if (sep.equals(";"))
				return;
			if (!sep.equals(","))
				throw new ReplayMOPException("expected ,");
		}
		throw new ReplayMOPException("expected ;");

	}

	void handleBeforeSync() throws ReplayMOPException {
		input.nextLine();
		while (input.hasNext()) {
			String func = input.nextLine().trim();
			if (func.equals("default"))
				spec.addBeforeSyncDefault();
			else if (func.equals("synchronized"))
				spec.beforeMonitorEnter = true;
			else if (func.equals("begin"))
				spec.beforeThreadEnd = true;
			else if (func.equals(";"))
				return;
			else
				spec.beforeSync.add(func);
		}
		throw new ReplayMOPException("expected ;");

	}

	void handleAfterSync() throws ReplayMOPException {
		input.nextLine();
		while (input.hasNext()) {
			String func = input.nextLine().trim();
			if (func.equals("default"))
				spec.addAfterSyncDefault();
			else if (func.equals("synchronized"))
				spec.afterMonitorExit = true;
			else if (func.equals("begin"))
				spec.afterThreadBegin = true;
			else if (func.equals(";"))
				return;
			else
				spec.afterSync.add(func);
		}
		throw new ReplayMOPException("expected ;");

	}

	void handleSchedule() throws ReplayMOPException {

		while (input.hasNext()) {
			ScheduleUnit unit = new ScheduleUnit();
			unit.thread = input.nextLong();
			if (!spec.threads.contains(unit.thread))
				throw new ReplayMOPException("thread id undefined");
			String sep = input.next();
			if (!sep.equals("x"))
				throw new ReplayMOPException("expected x");
			unit.count = input.nextInt();
			spec.schedule.add(unit);
			sep = input.next();
			if (sep.equals(";"))
				return;
			if (!sep.equals(","))
				throw new ReplayMOPException("expected ,");
		}
		throw new ReplayMOPException("expected ;");
	}

	void handleInput() {
		spec.input += input.nextLine() + "\n";
	}
	
	@Override
	protected void startParsing() throws ReplayMOPException {
		while (input.hasNext()) {
			String section = input.next();
			switch (section) {
			case "threads:":
				handleThreads();
				break;
			case "thread_creation_order:":
				handleThreadCreationOrder();
				break;
			case "shared:":
				handleShared();
				break;
			case "before_sync:":
				handleBeforeSync();
				break;
			case "after_sync:":
				handleAfterSync();
				break;
			case "schedule:":
				handleSchedule();
				break;
			case "input:":
				handleInput();
				break;
			default:
				throw new ReplayMOPException("unrecognized keyword");
			}
		}
		alreadyParsed = true;
	}


}
