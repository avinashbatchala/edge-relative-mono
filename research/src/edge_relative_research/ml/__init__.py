"""ML research: feature assembly, training, artifact export and evaluation.

The research layer trains gradient-boosted rankers and exports a dependency-free frozen tree artifact
(``er-gbm-v1``) that the Java backend evaluates in-process. Nothing here touches broker credentials or
authoritative trading state; it reads backtest exports and registers model versions.

LightGBM (and its numpy/scikit-learn stack) is imported lazily so the pure-Python artifact evaluator
and evaluation metrics remain importable without the optional ML toolchain.
"""
