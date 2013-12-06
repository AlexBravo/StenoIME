package com.brentandjody.Translator;

/**
 * Created by brent on 01/12/13.
 */
public abstract class Translator {

    public static enum TYPE {RawStrokes, SimpleDictionary}

    public abstract boolean usesDictionary();
    public abstract void lock();
    public abstract void unlock();
    public abstract TranslationResult submitQueue();
    public abstract TranslationResult translate(Stroke stroke);


}
