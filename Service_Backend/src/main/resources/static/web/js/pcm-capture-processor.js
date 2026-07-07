// Author: Xia Zihang
// PCM capture worklet: collects raw 16kHz/16-bit/mono PCM frames.
// The backend CharacterAsrService expects raw PCM (it adds the WAV header itself).
class PcmCaptureProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const input = inputs[0];
    if (input && input[0]) {
      // Downsample note: the AudioContext is created at 16000Hz, so input is already 16kHz.
      // Convert Float32 [-1,1] to Int16 and post back.
      const f32 = input[0];
      const i16 = new Int16Array(f32.length);
      for (let i = 0; i < f32.length; i++) {
        let s = Math.max(-1, Math.min(1, f32[i]));
        i16[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
      }
      this.port.postMessage(i16.buffer, [i16.buffer]);
    }
    return true;
  }
}
registerProcessor("pcm-capture-processor", PcmCaptureProcessor);
