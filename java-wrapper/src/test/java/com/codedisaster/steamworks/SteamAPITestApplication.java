package com.codedisaster.steamworks;

import java.util.Scanner;

public class SteamAPITestApplication {

	private SteamUser user;
	private SteamUserStats userStats;
	private SteamRemoteStorage remoteStorage;
	private SteamUGC ugc;
	private SteamUtils utils;

	private SteamUserStatsCallback userStatsCallback = new SteamUserStatsCallback() {
		@Override
		public void onUserStatsReceived(long gameId, long userId, SteamResult result) {
			System.out.println("User stats received: gameId=" + gameId + ", userId=" + userId +
					", result=" + result.toString());

			int numAchievements = userStats.getNumAchievements();
			System.out.println("Num of achievements: " + numAchievements);

			for (int i = 0; i < numAchievements; i++) {
				String name = userStats.getAchievementName(i);
				boolean achieved = userStats.isAchieved(name, false);
				System.out.println("# " + i + " : name=" + name + ", achieved=" + (achieved ? "yes" : "no"));
			}
		}

		@Override
		public void onUserStatsStored(long gameId, SteamResult result) {
			System.out.println("User stats stored: gameId=" + gameId +
					", result=" + result.toString());
		}
	};

	private SteamUGCCallback ugcCallback = new SteamUGCCallback() {
		@Override
		public void onUGCQueryCompleted(SteamUGCQuery query, int numResultsReturned, int totalMatchingResults,
										boolean isCachedData, SteamResult result) {
			System.out.println("UGC query completed: handle= " + query.handle + ", " + numResultsReturned + " of " +
							   totalMatchingResults + " results returned, result=" + result.toString());

			ugc.releaseQueryUserUGCRequest(query);
		}
	};

	class InputHandler implements Runnable {

		private volatile boolean alive;
		private Thread mainThread;
		private Scanner scanner;

		public InputHandler(Thread mainThread) {
			this.alive = true;
			this.mainThread = mainThread;

			this.scanner = new Scanner(System.in);
			scanner.useDelimiter("[\r\n\t]");
		}

		@Override
		public void run() {
			while (alive && mainThread.isAlive()) {

				if (scanner.hasNext()) {
					String input = scanner.next();

					if (input.equals("q")) {
						alive = false;
					} else if (input.equals("stats request")) {
						userStats.requestCurrentStats();
					} else if (input.equals("stats store")) {
						userStats.storeStats();
					} else if (input.equals("file list")) {
						int numFiles = remoteStorage.getFileCount();
						System.out.println("Num of files: " + numFiles);

						for (int i = 0; i < numFiles; i++) {
							int[] sizes = new int[1];
							String name = remoteStorage.getFileNameAndSize(i, sizes);
							boolean exists = remoteStorage.fileExists(name);
							System.out.println("# " + i + " : name=" + name + ", size=" + sizes[0] + ", exists=" + (exists ? "yes" : "no"));
						}
					} else if (input.equals("ugc query")) {
						SteamUGCQuery query = ugc.createQueryUserUGCRequest(user.getSteamID().getAccountID(), SteamUGC.UserUGCList.Subscribed,
								SteamUGC.MatchingUGCType.UsableInGame, SteamUGC.UserUGCListSortOrder.TitleAsc,
								utils.getAppID(), utils.getAppID(), 1);

						if (query.isValid()) {
							System.out.println("sending UGC query: " + query.handle);
							ugc.setReturnTotalOnly(query, true);
							ugc.sendQueryUGCRequest(query);
						}
					}
				}

			}
		}

		public boolean alive() {
			return alive;
		}
	}

	private boolean run(@SuppressWarnings("unused") String[] arguments) throws SteamException {

		System.out.println("Initialise Steam API ...");
		if (!SteamAPI.init()) {
			return false;
		}

		System.out.println("Register user ...");
		user = new SteamUser(SteamAPI.getSteamUserPointer());

		System.out.println("Register user stats callback ...");
		userStats = new SteamUserStats(SteamAPI.getSteamUserStatsPointer(), userStatsCallback);

		//System.out.println("Requesting user stats ...");
		//userStats.requestCurrentStats();

		System.out.println("Register remote storage ...");
		remoteStorage = new SteamRemoteStorage(SteamAPI.getSteamRemoteStoragePointer());

		System.out.println("Register UGC ...");
		ugc = new SteamUGC(SteamAPI.getSteamUGCPointer(), ugcCallback);

		System.out.println("Register Utils ...");
		utils = new SteamUtils(SteamAPI.getSteamUtilsPointer());

		System.out.println("Local user account ID: " + user.getSteamID().getAccountID());
		System.out.println("App ID: " + utils.getAppID());

		InputHandler inputHandler = new InputHandler(Thread.currentThread());
		new Thread(inputHandler).start();

		while (inputHandler.alive() && SteamAPI.isSteamRunning()) {

			// process callbacks
			SteamAPI.runCallbacks();

			try {
				// sleep a little (Steam says it should poll at least 15 times/second)
				Thread.sleep(1000 / 15);
			} catch (InterruptedException e) {
				// ignore
			}
		}

		System.out.println("Shutting down Steam API ...");
		SteamAPI.shutdown();

		System.out.println("Bye!");
		return true;
	}

	public static void main(String[] arguments) {

		try {

			if (!new SteamAPITestApplication().run(arguments)) {
				System.exit(-1);
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

}