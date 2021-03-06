package com.guitar_tuner_tv.guitartunertv;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchProcessor;

import static be.tarsos.dsp.io.android.AudioDispatcherFactory.fromDefaultMicrophone;
import static be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm.YIN;

/**
 * Gets the mic input and the current frequency.
 * Created by sbarjola on 18/08/2018.
 */
public class AudioController extends AsyncTask<Float, Float, Float> {

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 4096;
    private static final int RECORD_OVERLAPS = 3072;
    private static final int MIN_SIMILAR_TAKES = 50;
    private static final float FREQ_TOLERANCE = 200F;

    private static List<Float> recordedFrequencies = new ArrayList<>();
    private static PublisherResults resultsPublisher;

    public AudioController(Activity mActivity) {
        resultsPublisher = (PublisherResults) mActivity;
    }

    interface PublisherResults {
        void onFrequenceTranslated(final Float frecuencia);
    }

    static boolean isMicRecording;
    private AudioDispatcher audioDispatcher;

    @Override
    protected Float doInBackground(Float... params) {

        PitchDetectionHandler pitchDetector = (pitchDetectionResult, audioEvent) -> {

            if (isCancelled()) {
                stopAudioDispatcher();
                return;
            }

            if (!isMicRecording) {
                isMicRecording = true;
                publishProgress();
            }

            final float detectedFrequency = pitchDetectionResult.getPitch();
            Log.e("FRECUENCIA TEST:", String.valueOf(detectedFrequency));

            if (detectedFrequency != -1) {

                recordedFrequencies.add(detectedFrequency);

                if (recordedFrequencies.size() >= MIN_SIMILAR_TAKES) {
                    final Float medianFrequency = processAudioCaptured(recordedFrequencies);
                    publishProgress(medianFrequency);
                    recordedFrequencies.clear();

                    Log.e("FRECUENCIA GOOD TEST:", String.valueOf(medianFrequency));
                }
            }
        };

        PitchProcessor pitchProcessor = new PitchProcessor(YIN, SAMPLE_RATE, BUFFER_SIZE, pitchDetector);

        audioDispatcher = fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, RECORD_OVERLAPS);
        audioDispatcher.addAudioProcessor(pitchProcessor);
        audioDispatcher.run();

        return null;
    }

    @Override
    protected void onCancelled(Float result) {
        stopAudioDispatcher();
    }

    @Override
    protected void onProgressUpdate(Float... frequencies) {
        if (resultsPublisher != null) {
            if (frequencies.length > 0) {
                resultsPublisher.onFrequenceTranslated(frequencies[0]);
            } else {
                resultsPublisher.onFrequenceTranslated(null);
            }
        }
    }

    private void stopAudioDispatcher() {
        if (audioDispatcher != null && !audioDispatcher.isStopped()) {
            audioDispatcher.stop();
            isMicRecording = false;
        }
    }

    /**
     * Calcula la frecuencia mediana de las tomas obtenidas de audio.
     *
     * @param tomasAudio frecuencias obtenidas.
     * @return mediana de ellas.
     */
    private static Float processAudioCaptured(List<Float> tomasAudio) {

        // Pasamos la list a array
        float[] arrayValores = floatListToArray(tomasAudio);
        float medianValue = calculateArrayMedian(arrayValores);

        // Volvemos a hacer otra pasada por el array, pero eliminando
        // los valores que consideramos ruido y escapan de nuestra tolerancia
        for (int iterIndex = 0; iterIndex < tomasAudio.size(); iterIndex++) {
            final Float currentFloat = tomasAudio.get(iterIndex);
            if (currentFloat < medianValue - FREQ_TOLERANCE || currentFloat > medianValue + FREQ_TOLERANCE) {
                tomasAudio.remove(iterIndex);
            }
        }

        arrayValores = floatListToArray(tomasAudio);
        medianValue = calculateArrayMedian(arrayValores);

        return medianValue;
    }

    /**
     * Metodo que transforma una lista de Floats a un array de Floats.
     *
     * @param floatList lista
     * @return array de floats
     */
    private static float[] floatListToArray(List<Float> floatList) {

        float[] arrayValores = new float[floatList.size()];

        int i = 0;

        for (final Float f : floatList) {
            arrayValores[i++] = (f != null ? f : Float.NaN);
        }

        return arrayValores;
    }

    /**
     * Calcula la mediana de los valores de un array de Floats.
     *
     * @param floatArray array de Floats
     * @return mediana resultante
     */
    private static Float calculateArrayMedian(float[] floatArray) {

        float medianValue = 0;

        // Ordenamos
        Arrays.sort(floatArray);

        // Calculamos la mediana
        final int median = floatArray.length / 2;

        if (floatArray.length % 2 == 1) {
            medianValue = floatArray[median];
        } else {
            medianValue = (floatArray[median - 1] + floatArray[median]) / 2;
        }

        return medianValue;
    }

    public static void setIsMicRecording(boolean isMicRecording) {
        AudioController.isMicRecording = isMicRecording;
    }
}