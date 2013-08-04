/*
 * rtl_tcp_andro is an Android port of the famous rtl_tcp driver for 
 * RTL2832U based USB DVB-T dongles. It does not require root.
 * Copyright (C) 2012 by Martin Marinov <martintzvetomirov@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package marto.rtl_tcp_andro.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.util.Log;

/**
 * Runs a binary and monitors its output.
 * 
 * @author martinmarinov
 *
 */
public class ProcessRunner {

	private final static String TAG = "rtl_tcp_andro";
	
	private final String exeloc;
	private final String args;
	private Process process = null;
	private OnProcessTalkCallback callback = null;
	private final Object locker2 = new Object();
	private final String executable;
	private final boolean root;
	private OutputStreamWriter su_instream;
	
	private final HashMap<OnProcessSaidWord, String> wordcallbacks = new HashMap<OnProcessSaidWord, String>();

	private String last_stdout_line = "";
	
	public ProcessRunner(final String[] libraries, final String executable, final String arguments, final Context ctx, final boolean root) throws IOException {
		this(libraries, executable, arguments, ctx, null, root);
	}
	
	/**
	 * Runs a binary from the assets folder and controls it.
	 * @param libraries the libraries that the executable depends on
	 * @param executable the executable itself
	 * @param argumentss the arguments that would be passed to the executable
	 * @throws IOException if we can't copy the files to a safe directory
	 */
	public ProcessRunner(final String[] libraries, final String executable, final String argumentss, final Context ctx, final OnProcessTalkCallback callback, final boolean root) throws IOException {
		this.root = root;
		final String folder = ctx.getFilesDir().getAbsolutePath();
		
		for (int i = 0; i < libraries.length; i++) copyFile(libraries[i], folder+"/"+libraries[i], ctx);
		copyFile(executable, folder+"/"+executable, ctx);
		

		Runtime.getRuntime().exec("chmod 777 "+folder+"/"+executable);
		Runtime.getRuntime().exec("chmod +X "+folder+"/"+executable);
		
		exeloc = folder+"/"+executable;
		args = argumentss;
		
		this.callback = callback;		
		this.executable = executable.trim();
	}
	
	/**
	 * Start the binary if it is not running.
	 * @throws Exception
	 */
	public void start() throws Exception {
		Integer ev = null;
		try {ev = process.exitValue();} catch (Exception e) {};
		
		if (process == null || ev != null) {
			
			// IMPORTANT! This is not going to work if there are library dependencies!!!! KEEP THIS IN MIND!
			final String[] args_split = this.args.split(" ");
			final ArrayList<String> args = new ArrayList<String>();
			args.add(exeloc);
			for (String arg : args_split) {
				arg = arg.trim();
				if (arg.equals("")) continue;
				args.add(arg);
			}
			
			if (root) {
				final StringBuilder together = new StringBuilder();
				for (final String s : args) together.append(s+" ");
				final ProcessBuilder pb = new ProcessBuilder("su");
				pb.redirectErrorStream(true);
				process = pb.start();
				su_instream  = new OutputStreamWriter(process.getOutputStream());
				su_instream.write(together.toString()+"\n");
				su_instream.write("exit\n");
				su_instream.flush();
			} else {
				final ProcessBuilder pb = new ProcessBuilder(args.toArray(new String[0]));
				// add library dependency handling to pb's environment if you want to use binary with library dependencies!
				pb.redirectErrorStream(true);
				process = pb.start();
			}
			
			startOutputRedirector(process.getInputStream());
		}
	}
	
	/**
	 * Is the binary running
	 * @return
	 */
	public boolean isRunning() {
		Integer ev = null;
		try {ev = process.exitValue();} catch (Exception e) {};
		
		return process != null && ev == null;
	}
	
	public void registerWordCallback(final OnProcessSaidWord callback, final String word) {
		wordcallbacks.put(callback, word);
	}
	
	public void unregisterWordCallback(final OnProcessSaidWord callback) {
		wordcallbacks.remove(callback);
	}
	
	private static void copyFile(String assetPath, String localPath, Context context) throws IOException {
		try {
			final InputStream in = context.getAssets().open(assetPath);
			final FileOutputStream out = new FileOutputStream(localPath);
			int read;
			byte[] buffer = new byte[4096];
			while ((read = in.read(buffer)) > 0) {
				out.write(buffer, 0, read);
			}
			out.close();
			in.close();
		} catch (IOException e) {
			if (! new File(localPath).exists())
				throw e;
		}
	}
	
	private void startOutputRedirector(final InputStream in) {
		new Thread() {
			public void run() {
				last_stdout_line = "";
				
				try {	
					final BufferedReader bri = new BufferedReader
							(new InputStreamReader(in));

					while (true) {
						
						synchronized (locker2) {
							if ((last_stdout_line = bri.readLine()) == null)
								break;
						}

						if (callback != null) callback.OnProcessTalk(last_stdout_line);
						
						for (final OnProcessSaidWord call : wordcallbacks.keySet())
							if (last_stdout_line.contains(wordcallbacks.get(call)))
								call.OnProcessSaid(last_stdout_line);
					}

					bri.close();
				} catch (Exception e) {}
				
				if (process != null) {
					try { process.waitFor(); } catch (InterruptedException e) {}

					Log.d(TAG, executable+" exit("+process.exitValue()+")");
					if (callback != null) callback.OnClosed(process.exitValue());
					for (final OnProcessSaidWord call : wordcallbacks.keySet()) call.OnClosed(process.exitValue());
				}
				
				last_stdout_line = null;
			};
		}.start();
	}
	
	
	public void blockUntilFinished() {
		if (!isRunning())
			return;
		try {
			process.waitFor();
		} catch (InterruptedException e) {}
	}
	
	public void stop(final Context ctx) {
		
		try {
			if (root) {
				
				su_instream.close();

				// NOT CLOSING PROPERLY!!!!!!!!!!!!!!!!!!!!!!
				runRootCommand("killall -SIGINT "+executable);
				
			}
		} catch (Throwable e) {};

		try {
			process.destroy();
		} catch (Throwable e) {};
	
	}
	
	public static interface OnProcessTalkCallback {
		/** Whenever the process writes something to its stdout, this will get called */
		void OnProcessTalk(final String line);
		
		void OnClosed(final int exitvalue);

	}
	
	/**
	 * A callback whenever the process outputs a line containing a certain word onto its standard output or closes.
	 */
	public static interface OnProcessSaidWord {
		void OnProcessSaid(final String line);
		
		void OnClosed(final int exitvalue);

	}
	
	private static void runRootCommand(final String command) throws IOException {
		final ProcessBuilder pb = new ProcessBuilder("su");
		pb.redirectErrorStream(true);
		final Process proc = pb.start();
		final OutputStreamWriter su2 = new OutputStreamWriter(proc.getOutputStream());
		su2.write(command+"\nexit\n");
		su2.flush();
		su2.close();
		
		final BufferedReader bri = new BufferedReader
				(new InputStreamReader(proc.getInputStream()));
		
		while (true) {
			final String line = bri.readLine();
			if (line == null) break;
			Log.w(TAG, "su: "+line);
		}
	}
	
}
