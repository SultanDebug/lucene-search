package com.hzq.researchtest.test;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.Builder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

/**
 * @author Huangzq
 * @description
 * @date 2022/11/16 15:04
 */
public class FSTTest {
    public static void main(String[] args) throws Exception {
        String[] inputValues = {"mop", "moth", "pop", "star", "stop", "top"};
        long[] outputValues = {100, 91, 72, 83, 54, 55};
        PositiveIntOutputs outputs = PositiveIntOutputs.getSingleton();
        Builder<Long> builder = new Builder<>(FST.INPUT_TYPE.BYTE1, outputs);
        IntsRefBuilder scratchInts = new IntsRefBuilder();
        for (int i = 0; i < inputValues.length; i++) {
            builder.add(Util.toIntsRef(new BytesRef(inputValues[i]), scratchInts), outputValues[i]);
        }
        FST<Long> fst = builder.finish();
        byte[] current = new byte[41];
        FST.BytesReader reader = fst.getBytesReader();
        reader.setPosition(40);
        reader.readBytes(current, 0, current.length - 1);
        System.out.print("current数组中的值为：");
        for (int i = current.length - 1; i >= 0; i--) {
            byte b = current[i];
            System.out.print(b + " ");
        }
        System.out.println("");
        Long value = Util.get(fst, new BytesRef("mop"));
        System.out.println(value);

    }
}
