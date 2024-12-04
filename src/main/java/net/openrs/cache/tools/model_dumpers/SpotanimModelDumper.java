/**
* Copyright (c) Kyle Fricilone
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.openrs.cache.tools.model_dumpers;

import net.openrs.cache.*;
import net.openrs.cache.type.identkits.IdentkitType;
import net.openrs.cache.type.identkits.IdentkitTypeList;
import net.openrs.cache.type.spotanims.SpotAnimType;
import net.openrs.cache.type.spotanims.SpotAnimTypeList;
import net.openrs.cache.util.CompressionUtils;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Dumps only models for npcs
 * 
 * @author Freyr
 *
 * @since 04/01/2017
 */
public class SpotanimModelDumper {

	public static void main(String[] args) throws IOException {
		File directory = new File(Constants.MODEL_PATH + "/spotanim/");
		
		if (!directory.exists()) {
			directory.mkdirs();
		}
		
		try (Cache cache = new Cache(FileStore.open(Constants.CACHE_PATH))) {
			SpotAnimTypeList list = new SpotAnimTypeList();

			list.initialize(cache);

			Set<Integer> set = new HashSet<>();

			for (int i = 0; i < list.size(); i++) {

				SpotAnimType idk = list.list(i);
				
				if (idk == null) {
					continue;
				}
				if (idk.getModelID() != 0) {
					set.add(idk.getModelID());
				}

			}

			ReferenceTable table = cache.getReferenceTable(7);

			Iterator<Integer> itr = set.iterator();
			
			int count = 0;
			
			int size = set.size();

			while (itr.hasNext()) {

				int i = itr.next();

				if (table.getEntry(i) == null) {
					continue;
				}

				Container container = cache.read(7, i);

				byte[] bytes = new byte[container.getData().limit()];
				container.getData().get(bytes);

				try(DataOutputStream dos = new DataOutputStream(new FileOutputStream(new File(directory, i + ".gz")))) {
					dos.write(CompressionUtils.gzip(bytes));
				}
				
				count++;
				
				double progress = (double) (count) / size * 100;
				
				System.out.printf("%.2f%s\n", progress, "%");

				itr.remove();
			}

		}
	}

}
